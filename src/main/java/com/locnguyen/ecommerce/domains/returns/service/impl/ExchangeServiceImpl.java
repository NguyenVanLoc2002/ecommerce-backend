package com.locnguyen.ecommerce.domains.returns.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import com.locnguyen.ecommerce.domains.notification.service.NotificationService;
import com.locnguyen.ecommerce.domains.order.dto.OrderItemResponse;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.payment.service.PaymentRefundService;
import com.locnguyen.ecommerce.domains.productvariant.service.ProductVariantService;
import com.locnguyen.ecommerce.domains.returns.dto.ExchangeResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ExchangeSelection;
import com.locnguyen.ecommerce.domains.returns.entity.OrderReturn;
import com.locnguyen.ecommerce.domains.returns.entity.ReturnExchange;
import com.locnguyen.ecommerce.domains.returns.enums.ExchangeStatus;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.repository.OrderReturnRepository;
import com.locnguyen.ecommerce.domains.returns.repository.ReturnExchangeRepository;
import com.locnguyen.ecommerce.domains.returns.repository.ReturnHandlingRepository;
import com.locnguyen.ecommerce.domains.returns.service.ExchangeService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeServiceImpl implements ExchangeService {
    private final OrderService orderService;
    private final PaymentRefundService refundService;
    private final OrderReturnRepository returnRepository;
    private final ReturnExchangeRepository exchangeRepository;
    private final ReturnHandlingRepository handlingRepository;
    private final InventoryService inventoryService;
    private final ProductVariantService variantService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ExchangeResponse reserve(UUID orderId, UUID returnId, String key, String actor, ExchangeSelection selection) {
        if (key == null || key.isBlank()) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String requestKey = key.strip();
        if (requestKey.length() > 100) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_TOO_LONG);
        }
        orderService.lockOrderForReturn(orderId);
        OrderReturn entry = findReturn(orderId, returnId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        var exchanges = exchangeRepository.findByReturnId(returnId);
        for (ReturnExchange previous : exchanges) {
            if (previous.getRequestKey().equals(requestKey)) {
                if (!matches(previous, actor, selection)) {
                    throw new AppException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
                }
                return response(previous, now);
            }
        }
        if (refundService.findReturnRefund(returnId).isPresent() || entry.getReason() == ReturnReason.MISSING_ITEM
                || (entry.getStatus() != ReturnStatus.APPROVED && entry.getStatus() != ReturnStatus.INSPECTED)) {
            throw new AppException(ErrorCode.RETURN_DECISION_INVALID);
        }

        int maximum = allowedQuantity(entry, selection.orderItemId());
        long held = exchanges.stream().filter(row -> row.getOrderItemId().equals(selection.orderItemId()))
                .filter(row -> effectiveStatus(row, now) == ExchangeStatus.RESERVED
                        || row.getStatus() == ExchangeStatus.REPLACEMENT_CREATED)
                .mapToLong(ReturnExchange::getQuantity).sum();
        if (selection.quantity() <= 0 || selection.quantity() > maximum - held) {
            throw new AppException(ErrorCode.RETURN_QUANTITY_UNAVAILABLE);
        }
        OrderItemResponse item = orderService.getOrderByIdAdmin(orderId).getItems().stream()
                .filter(line -> line.getId().equals(selection.orderItemId())).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_NOT_ELIGIBLE));
        if (selection.variantId().equals(item.getVariantId()) || item.getProductId() == null
                || variantService.getVariantsByProduct(item.getProductId(), false, false).stream()
                    .noneMatch(variant -> selection.variantId().equals(variant.getId()))) {
            throw new AppException(ErrorCode.RETURN_NOT_ELIGIBLE, "Select another variant of the purchased product");
        }

        var price = orderService.getExchangePriceSnapshot(orderId, item.getId());
        var alternatePrice = price.variantBasePrices() == null ? null : price.variantBasePrices().get(selection.variantId());
        boolean evidence = selection.historicalPriceEvidence() != null && !selection.historicalPriceEvidence().isBlank();
        ExchangeStatus status = alternatePrice == null && !evidence
                ? ExchangeStatus.PRICE_REVIEW_REQUIRED : ExchangeStatus.WAITING_CUSTOMER_CHOICE;
        if (alternatePrice != null && alternatePrice.compareTo(price.purchasedBasePrice()) != 0) {
            status = ExchangeStatus.DIFFERENT_PRICE;
        }
        ReturnExchange exchange = new ReturnExchange();
        exchange.setReturnId(returnId);
        exchange.setOrderItemId(selection.orderItemId());
        exchange.setVariantId(selection.variantId());
        exchange.setWarehouseId(selection.warehouseId());
        exchange.setQuantity(selection.quantity());
        exchange.setRequestKey(requestKey);
        exchange.setActor(actor);
        exchange.setHistoricalPriceEvidence(selection.historicalPriceEvidence());
        exchange.setStatus(status);
        exchangeRepository.save(exchange);

        if (status == ExchangeStatus.WAITING_CUSTOMER_CHOICE) {
            inventoryService.tryReserveExchangeStock(selection.variantId(), selection.warehouseId(),
                    selection.quantity(), exchange.getId()).ifPresent(expiresAt -> {
                        exchange.setExpiresAt(expiresAt);
                        exchange.setStatus(ExchangeStatus.RESERVED);
                    });
        }
        if (exchange.getStatus() == ExchangeStatus.WAITING_CUSTOMER_CHOICE) {
            notificationService.recordReturnUpdate(entry.getSubmittedByCustomerId(), returnId,
                    "The replacement is out of stock. Choose another option or request a refund.");
        }
        return response(exchange, now);
    }

    @Override
    @Transactional
    public ExchangeResponse cancel(UUID orderId, UUID returnId, UUID exchangeId, String actor) {
        orderService.lockOrderForReturn(orderId);
        findReturn(orderId, returnId);
        ReturnExchange exchange = exchangeRepository.findById(exchangeId)
                .filter(row -> row.getReturnId().equals(returnId))
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (exchange.getStatus() == ExchangeStatus.REPLACEMENT_CREATED) {
            throw new AppException(ErrorCode.RETURN_DECISION_INVALID, "A replacement order already exists");
        }
        if (exchange.getStatus() != ExchangeStatus.CANCELLED) {
            if (exchange.getStatus() == ExchangeStatus.RESERVED) {
                inventoryService.releaseStock("EXCHANGE", exchange.getId().toString());
            }
            exchange.setStatus(ExchangeStatus.CANCELLED);
            exchange.setCancelledAt(now);
            exchange.setCancelledBy(actor);
        }
        return response(exchange, now);
    }

    @Override
    @Transactional
    public ExchangeResponse createReplacement(UUID orderId, UUID returnId, UUID exchangeId) {
        orderService.lockOrderForReturn(orderId);
        OrderReturn entry = findReturn(orderId, returnId);
        ReturnExchange exchange = exchangeRepository.findById(exchangeId)
                .filter(row -> row.getReturnId().equals(returnId))
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (exchange.getStatus() == ExchangeStatus.REPLACEMENT_CREATED) {
            return response(exchange, now);
        }
        if (entry.getStatus() != ReturnStatus.INSPECTED || exchange.getStatus() != ExchangeStatus.RESERVED) {
            throw new AppException(ErrorCode.RETURN_DECISION_INVALID, "Inspection and stock reservation are required");
        }
        long alreadyReplaced = exchangeRepository.findByReturnId(returnId).stream()
                .filter(row -> row.getStatus() == ExchangeStatus.REPLACEMENT_CREATED
                        && row.getOrderItemId().equals(exchange.getOrderItemId()))
                .mapToLong(ReturnExchange::getQuantity).sum();
        if (exchange.getQuantity() > allowedQuantity(entry, exchange.getOrderItemId()) - alreadyReplaced) {
            throw new AppException(ErrorCode.RETURN_QUANTITY_UNAVAILABLE);
        }
        if (effectiveStatus(exchange, now) == ExchangeStatus.HOLD_EXPIRED) {
            return response(exchange, now);
        }
        var replacement = orderService.createExchangeReplacement(orderId, exchange.getOrderItemId(),
                exchangeId, exchange.getVariantId(), exchange.getQuantity());
        if (replacement.isEmpty()) {
            exchange.setStatus(ExchangeStatus.WAITING_CUSTOMER_CHOICE);
            notificationService.recordReturnUpdate(entry.getSubmittedByCustomerId(), returnId,
                    "The replacement hold is unavailable. Choose another option or request a refund.");
            return response(exchange, now);
        }
        exchange.setReplacementOrderId(replacement.get());
        exchange.setStatus(ExchangeStatus.REPLACEMENT_CREATED);
        notificationService.recordReturnUpdate(entry.getSubmittedByCustomerId(), returnId,
                "Your replacement order was created.");
        return response(exchange, now);
    }

    private int allowedQuantity(OrderReturn entry, UUID itemId) {
        if (entry.getStatus() == ReturnStatus.INSPECTED) {
            return handlingRepository.findByReturnIdAndNextStatus(entry.getId(), ReturnStatus.INSPECTED)
                    .orElseThrow(() -> new AppException(ErrorCode.RETURN_DECISION_INVALID)).getItems().stream()
                    .filter(line -> line.orderItemId().equals(itemId))
                    .mapToInt(line -> entry.getReason() == ReturnReason.CHANGED_MIND ? line.restock() : line.quantity())
                    .sum();
        }
        return entry.getLines().stream().filter(line -> line.orderItemId().equals(itemId))
                .mapToInt(line -> line.quantity()).sum();
    }

    private OrderReturn findReturn(UUID orderId, UUID returnId) {
        return returnRepository.findById(returnId).filter(row -> row.getOrderId().equals(orderId))
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_NOT_FOUND));
    }

    private boolean matches(ReturnExchange row, String actor, ExchangeSelection selection) {
        return row.getActor().equals(actor) && row.getOrderItemId().equals(selection.orderItemId())
                && row.getVariantId().equals(selection.variantId()) && row.getWarehouseId().equals(selection.warehouseId())
                && row.getQuantity() == selection.quantity()
                && Objects.equals(row.getHistoricalPriceEvidence(), selection.historicalPriceEvidence());
    }

    private ExchangeStatus effectiveStatus(ReturnExchange row, LocalDateTime now) {
        return row.getStatus() == ExchangeStatus.RESERVED && !row.getExpiresAt().isAfter(now)
                ? ExchangeStatus.HOLD_EXPIRED : row.getStatus();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<ExchangeResponse> listForReturn(UUID returnId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return exchangeRepository.findByReturnId(returnId).stream()
                .map(row -> response(row, now))
                .toList();
    }

    private ExchangeResponse response(ReturnExchange row, LocalDateTime now) {
        return new ExchangeResponse(row.getId(), row.getReturnId(), row.getOrderItemId(), row.getVariantId(),
                row.getWarehouseId(), row.getQuantity(), effectiveStatus(row, now), row.getExpiresAt(), row.getReplacementOrderId());
    }
}
