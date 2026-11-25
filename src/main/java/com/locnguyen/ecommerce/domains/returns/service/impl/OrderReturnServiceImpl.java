package com.locnguyen.ecommerce.domains.returns.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.notification.service.NotificationService;
import com.locnguyen.ecommerce.domains.order.dto.OrderItemResponse;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.returns.dto.CreateReturnRequest;
import com.locnguyen.ecommerce.domains.returns.dto.CustomerReturnDetail;
import com.locnguyen.ecommerce.domains.returns.dto.CustomerReturnsResponse;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnResponse;
import com.locnguyen.ecommerce.domains.returns.dto.AdminReturnDetail;
import com.locnguyen.ecommerce.domains.returns.service.ExchangeService;
import com.locnguyen.ecommerce.domains.payment.service.PaymentRefundService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.locnguyen.ecommerce.domains.returns.entity.OrderReturn;
import com.locnguyen.ecommerce.domains.returns.entity.ReturnLine;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnPolicy;
import com.locnguyen.ecommerce.domains.returns.repository.OrderReturnRepository;
import com.locnguyen.ecommerce.domains.returns.service.OrderReturnService;
import com.locnguyen.ecommerce.domains.returns.service.ReturnEligibility;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnDecisionRequest;
import com.locnguyen.ecommerce.domains.returns.entity.ReturnDecision;
import com.locnguyen.ecommerce.domains.returns.repository.ReturnDecisionRepository;
import java.time.temporal.ChronoUnit;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import com.locnguyen.ecommerce.domains.returns.dto.ReturnHandlingRequest;
import com.locnguyen.ecommerce.domains.returns.entity.ReturnHandling;
import com.locnguyen.ecommerce.domains.returns.entity.ReturnHandlingLine;
import com.locnguyen.ecommerce.domains.returns.repository.ReturnHandlingRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderReturnServiceImpl implements OrderReturnService {
    private final OrderService orderService;
    private final ShipmentService shipmentService;
    private final OrderReturnRepository returnRepository;
    private final ReturnDecisionRepository decisionRepository;
    private final ReturnHandlingRepository handlingRepository;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final PaymentRefundService paymentRefundService;
    private final ExchangeService exchangeService;

    @Override
    @Transactional
    public ReturnResponse decide(UUID orderId, UUID returnId, String key, String actor,
                                 ReturnStatus next, ReturnDecisionRequest request) {
        String requestKey = requireKey(key);
        var orderStatus = orderService.lockOrderForReturn(orderId);
        OrderReturn entry = findReturn(orderId, returnId);
        LocalDateTime verifiedAt = request.deliveredAt() == null
                ? null : request.deliveredAt().truncatedTo(ChronoUnit.MICROS);
        var replay = decisionRepository.findByReturnIdAndRequestKey(returnId, requestKey);
        if (replay.isPresent()) {
            ReturnDecision previous = replay.get();
            if (previous.getNextStatus() != next || !previous.getActor().equals(actor)
                    || !previous.getReason().equals(request.reason())
                    || !Objects.equals(previous.getVerifiedDeliveredAt(), verifiedAt)) {
                throw new AppException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return toResponse(entry);
        }

        if ((next != ReturnStatus.REQUESTED && next != ReturnStatus.APPROVED && next != ReturnStatus.REJECTED)
                || !entry.getStatus().canTransitionTo(next) || (next == ReturnStatus.REQUESTED) != (verifiedAt != null)) {
            throw new AppException(ErrorCode.RETURN_DECISION_INVALID);
        }
        if (verifiedAt != null) {
            LocalDateTime requestedAt = entry.getRequestedAt() == null ? entry.getCreatedAt() : entry.getRequestedAt();
            ReturnEligibility.initialStatus(orderStatus, verifiedAt, requestedAt);
        }

        ReturnDecision decision = new ReturnDecision();
        decision.setReturnId(returnId);
        decision.setRequestKey(requestKey);
        decision.setActor(actor);
        decision.setReason(request.reason());
        decision.setVerifiedDeliveredAt(verifiedAt);
        decision.setPreviousStatus(entry.getStatus());
        decision.setNextStatus(next);
        entry.setStatus(next);
        decisionRepository.save(decision);
        notifyCustomer(entry);
        return toResponse(entry);
    }

    @Override
    @Transactional
    public ReturnResponse create(UUID orderId, Customer customer, String key, CreateReturnRequest request) {
        key = requireKey(key);
        LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var order = orderService.lockOwnedOrderForReturn(orderId, customer);
        List<ReturnLine> lines = request.items().stream()
                .map(item -> new ReturnLine(item.orderItemId(), item.quantity()))
                .sorted(Comparator.comparing(ReturnLine::orderItemId)).toList();
        if (lines.stream().map(ReturnLine::orderItemId).distinct().count() != lines.size()) {
            throw new AppException(ErrorCode.RETURN_QUANTITY_UNAVAILABLE, "Each order item can appear only once");
        }

        List<OrderReturn> prior = returnRepository.findByOrderId(orderId);
        for (OrderReturn existing : prior) {
            if (existing.getRequestKey().equals(key)) {
                var existingLines = existing.getLines().stream()
                        .sorted(Comparator.comparing(ReturnLine::orderItemId)).toList();
                if (existing.getReason() != request.reason() || !existing.getCustomerNote().equals(request.note())
                        || existing.isOriginalCondition() != request.originalCondition()
                        || existing.isUnopened() != request.unopened()
                        || !existingLines.equals(lines)) {
                    throw new AppException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
                }
                return toResponse(existing);
            }
        }

        ReturnStatus status = ReturnEligibility.initialStatus(order.getStatus(), shipmentService.findDeliveredAt(orderId).orElse(null), requestedAt);
        Map<UUID, OrderItemResponse> orderItems = order.getItems().stream()
                .collect(Collectors.toMap(OrderItemResponse::getId, Function.identity()));
        Map<UUID, Long> claimed = claimedQuantities(prior);
        for (ReturnLine line : lines) {
            OrderItemResponse item = orderItems.get(line.orderItemId());
            if (item == null) {
                throw new AppException(ErrorCode.RETURN_NOT_ELIGIBLE, "The item does not belong to this order");
            }
            if (request.reason() == ReturnReason.CHANGED_MIND
                    && (!request.originalCondition() || !canChangeMind(item, request.unopened()))) {
                throw new AppException(ErrorCode.RETURN_NOT_ELIGIBLE, "Discretionary return policy does not allow this item");
            }
            ReturnEligibility.requireAvailableQuantity(item.getQuantity(),
                    claimed.getOrDefault(line.orderItemId(), 0L), line.quantity());
        }

        OrderReturn entry = new OrderReturn();
        entry.setOrderId(orderId);
        entry.setRequestedAt(requestedAt);
        entry.setSubmittedByCustomerId(customer.getId());
        entry.setRequestKey(key);
        entry.setReason(request.reason());
        entry.setCustomerNote(request.note());
        entry.setOriginalCondition(request.originalCondition());
        entry.setUnopened(request.unopened());
        entry.setStatus(status);
        entry.getLines().addAll(lines);
        returnRepository.save(entry);
        notifyCustomer(entry);
        return toResponse(entry);
    }

    @Override
    @Transactional
    public ReturnResponse handle(UUID orderId, UUID returnId, String key, String actor,
                                 ReturnStatus next, ReturnHandlingRequest request) {
        String requestKey = requireKey(key);
        orderService.lockOrderForReturn(orderId);
        OrderReturn entry = findReturn(orderId, returnId);
        if (next != ReturnStatus.RECEIVED && next != ReturnStatus.INSPECTED) {
            throw new AppException(ErrorCode.RETURN_DECISION_INVALID);
        }
        var items = request.items().stream().map(line -> new ReturnHandlingLine(
                line.orderItemId(), line.quantity(), line.restock(), line.damaged()))
                .sorted(Comparator.comparing(ReturnHandlingLine::orderItemId)).toList();
        LocalDateTime trackingAt = request.trackingAt() == null
                ? null : request.trackingAt().truncatedTo(ChronoUnit.MICROS);
        var prior = handlingRepository.findByReturnIdAndNextStatus(returnId, next);
        if (prior.isPresent()) {
            ReturnHandling operation = prior.get();
            if (!operation.getRequestKey().equals(requestKey) || !operation.getActor().equals(actor)
                    || !operation.getNote().equals(request.note())
                    || !Objects.equals(operation.getWarehouseId(), request.warehouseId())
                    || !Objects.equals(operation.getCarrier(), request.carrier())
                    || !Objects.equals(operation.getTrackingNumber(), request.trackingNumber())
                    || !Objects.equals(operation.getEvidenceReference(), request.evidenceReference())
                    || !Objects.equals(operation.getTrackingAt(), trackingAt)
                    || !operation.getItems().stream().sorted(Comparator.comparing(ReturnHandlingLine::orderItemId))
                            .toList().equals(items)) {
                throw new AppException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return toResponse(entry);
        }
        if (!entry.getStatus().canTransitionTo(next)
                || items.stream().map(ReturnHandlingLine::orderItemId).distinct().count() != items.size()) {
            throw new AppException(ErrorCode.RETURN_DECISION_INVALID);
        }
        if (handlingRepository.findByReturnIdAndNextStatus(returnId, ReturnStatus.RECEIVED)
                .filter(operation -> operation.getRequestKey().equals(requestKey)).isPresent()) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        if (next == ReturnStatus.RECEIVED) {
            validateReceipt(entry, items, request);
        } else {
            inspect(orderId, entry, items, request);
        }

        ReturnHandling operation = new ReturnHandling();
        operation.setReturnId(returnId);
        operation.setRequestKey(requestKey);
        operation.setNextStatus(next);
        operation.setActor(actor);
        operation.setNote(request.note());
        operation.setCarrier(request.carrier());
        operation.setTrackingNumber(request.trackingNumber());
        operation.setTrackingAt(trackingAt);
        operation.setEvidenceReference(request.evidenceReference());
        operation.setWarehouseId(request.warehouseId());
        operation.getItems().addAll(items);
        handlingRepository.save(operation);
        entry.setStatus(next);
        notifyCustomer(entry);
        return toResponse(entry);
    }

    private void notifyCustomer(OrderReturn entry) {
        String message = switch (entry.getStatus()) {
            case PENDING_DELIVERY_VERIFICATION -> "Your return request is awaiting delivery verification.";
            case REQUESTED -> "Your return request has been received for review.";
            case APPROVED -> "Your return request was approved.";
            case REJECTED -> "Your return request was rejected.";
            case RECEIVED -> "Your returned goods were received.";
            case INSPECTED -> "Inspection of your returned goods is complete.";
            case CANCELLED -> "Your return request was cancelled.";
            case COMPLETED -> "Your return request is complete.";
        };
        notificationService.recordReturnUpdate(entry.getSubmittedByCustomerId(), entry.getId(), message);
    }

    private OrderReturn findReturn(UUID orderId, UUID returnId) {
        return returnRepository.findById(returnId)
                .filter(candidate -> candidate.getOrderId().equals(orderId))
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_NOT_FOUND));
    }

    private void validateReceipt(OrderReturn entry, List<ReturnHandlingLine> items, ReturnHandlingRequest request) {
        var requested = entry.getLines().stream()
                .collect(Collectors.toMap(ReturnLine::orderItemId, ReturnLine::quantity));
        if (request.warehouseId() != null || items.stream().anyMatch(line ->
                line.quantity() <= 0 || line.quantity() > requested.getOrDefault(line.orderItemId(), 0)
                        || line.restock() != 0 || line.damaged() != 0)) {
            throw new AppException(ErrorCode.RETURN_DECISION_INVALID, "Invalid received quantities");
        }
    }

    private void inspect(UUID orderId, OrderReturn entry, List<ReturnHandlingLine> items,
                         ReturnHandlingRequest request) {
        var receipt = handlingRepository.findByReturnIdAndNextStatus(entry.getId(), ReturnStatus.RECEIVED)
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_DECISION_INVALID));
        var received = receipt.getItems().stream()
                .collect(Collectors.toMap(ReturnHandlingLine::orderItemId, ReturnHandlingLine::quantity));
        boolean restock = items.stream().anyMatch(line -> line.restock() > 0);
        if (items.size() != received.size() || restock != (request.warehouseId() != null)
                || request.carrier() != null || request.trackingNumber() != null || request.trackingAt() != null
                || items.stream().anyMatch(line -> line.quantity() != received.getOrDefault(line.orderItemId(), 0)
                        || line.restock() < 0 || line.damaged() < 0
                        || (long) line.restock() + line.damaged() != line.quantity())) {
            throw new AppException(ErrorCode.RETURN_DECISION_INVALID, "Inspection must account for received items");
        }
        if (restock) {
            var variants = orderService.getOrderByIdAdmin(orderId).getItems().stream()
                    .collect(Collectors.toMap(OrderItemResponse::getId, OrderItemResponse::getVariantId));
            items.stream().filter(line -> line.restock() > 0)
                    .sorted(Comparator.comparing(line -> variants.get(line.orderItemId())))
                    .forEach(line -> inventoryService.returnStock(variants.get(line.orderItemId()),
                            request.warehouseId(), line.restock(), "RETURN", entry.getId() + ":" + line.orderItemId()));
        }
    }

    private String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        key = key.strip();
        if (key.length() > 100) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_TOO_LONG);
        }
        return key;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnResponse> listForAdmin(ReturnStatus status, UUID orderId, Pageable pageable) {
        return returnRepository.findForAdmin(status, orderId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReturnDetail getForAdmin(UUID orderId, UUID returnId) {
        var entry = findReturn(orderId, returnId);
        return new AdminReturnDetail(toResponse(entry), getHandling(returnId, ReturnStatus.RECEIVED),
                getHandling(returnId, ReturnStatus.INSPECTED),
                paymentRefundService.findReturnRefund(returnId).orElse(null), exchangeService.listForReturn(returnId));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerReturnsResponse listForCustomer(UUID orderId, Customer customer) {
        var order = orderService.getOrderById(orderId, customer);
        var entries = returnRepository.findByOrderId(orderId);
        var requests = entries.stream().sorted(Comparator.comparing(OrderReturn::getCreatedAt).reversed())
                .map(this::toResponse).toList();
        var now = LocalDateTime.now(ZoneOffset.UTC);
        if (!ReturnEligibility.canSubmit(order.getStatus(), shipmentService.findDeliveredAt(orderId).orElse(null), now)) {
            return new CustomerReturnsResponse(List.of(), requests);
        }

        var claimed = claimedQuantities(entries);
        var eligibleItems = order.getItems().stream()
                .map(item -> new CustomerReturnsResponse.EligibleItem(item.getId(),
                        (int) Math.max(0, item.getQuantity() - claimed.getOrDefault(item.getId(), 0L)),
                        Arrays.stream(ReturnReason.values())
                                .filter(reason -> reason != ReturnReason.CHANGED_MIND || canChangeMind(item, true))
                                .toList(),
                        item.getReturnPolicySnapshot() == ReturnPolicy.SEALED_ONLY))
                .filter(item -> item.availableQuantity() > 0)
                .toList();
        return new CustomerReturnsResponse(eligibleItems, requests);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerReturnDetail getForCustomer(UUID orderId, UUID returnId, Customer customer) {
        orderService.getOrderById(orderId, customer);
        var entry = findReturn(orderId, returnId);
        var decisions = decisionRepository.findAllByReturnIdOrderByCreatedAtAsc(returnId);
        var receipt = handlingRepository.findByReturnIdAndNextStatus(returnId, ReturnStatus.RECEIVED);
        var inspection = handlingRepository.findByReturnIdAndNextStatus(returnId, ReturnStatus.INSPECTED);
        var refund = paymentRefundService.findReturnRefund(returnId)
                .map(value -> new CustomerReturnDetail.Refund(value.amount(), value.status(),
                        value.createdAt(), value.refundedAt())).orElse(null);
        var exchanges = exchangeService.listForReturn(returnId).stream()
                .map(value -> new CustomerReturnDetail.Exchange(value.id(), value.orderItemId(), value.quantity(),
                        value.status(), value.replacementOrderId())).toList();

        var timeline = new ArrayList<CustomerReturnDetail.Event>();
        if (!decisions.isEmpty()) {
            timeline.add(new CustomerReturnDetail.Event(decisions.get(0).getPreviousStatus(), entry.getCreatedAt()));
        } else if (entry.getStatus() == ReturnStatus.PENDING_DELIVERY_VERIFICATION || entry.getStatus() == ReturnStatus.REQUESTED) {
            timeline.add(new CustomerReturnDetail.Event(entry.getStatus(), entry.getCreatedAt()));
        }
        decisions.forEach(decision -> timeline.add(new CustomerReturnDetail.Event(
                decision.getNextStatus(), decision.getCreatedAt())));
        receipt.ifPresent(value -> timeline.add(new CustomerReturnDetail.Event(ReturnStatus.RECEIVED, value.getCreatedAt())));
        inspection.ifPresent(value -> timeline.add(new CustomerReturnDetail.Event(ReturnStatus.INSPECTED, value.getCreatedAt())));
        if (entry.getStatus() == ReturnStatus.COMPLETED || entry.getStatus() == ReturnStatus.CANCELLED) {
            timeline.add(new CustomerReturnDetail.Event(entry.getStatus(), entry.getUpdatedAt()));
        }
        timeline.sort(Comparator.comparing(CustomerReturnDetail.Event::occurredAt));

        var tracking = receipt.filter(value -> value.getCarrier() != null || value.getTrackingNumber() != null)
                .map(value -> new CustomerReturnDetail.Tracking(value.getCarrier(), value.getTrackingNumber(),
                        value.getTrackingAt())).orElse(null);
        var inspectedItems = inspection.stream().flatMap(value -> value.getItems().stream())
                .map(line -> new CustomerReturnDetail.InspectionLine(
                        line.orderItemId(), line.quantity(), line.restock(), line.damaged())).toList();
        return new CustomerReturnDetail(toResponse(entry), timeline, tracking, inspectedItems, refund, exchanges);
    }

    private Map<UUID, Long> claimedQuantities(List<OrderReturn> prior) {
        if (prior.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ReturnLine>> receivedLines = handlingRepository.findByReturnIdInAndNextStatus(
                        prior.stream().map(OrderReturn::getId).toList(), ReturnStatus.RECEIVED).stream()
                .collect(Collectors.toMap(ReturnHandling::getReturnId, operation -> operation.getItems().stream()
                        .map(item -> new ReturnLine(item.orderItemId(), item.quantity())).toList()));
        return prior.stream()
                .filter(entry -> entry.getStatus() != ReturnStatus.REJECTED && entry.getStatus() != ReturnStatus.CANCELLED)
                .flatMap(entry -> receivedLines.getOrDefault(entry.getId(), entry.getLines()).stream())
                .collect(Collectors.groupingBy(ReturnLine::orderItemId, Collectors.summingLong(ReturnLine::quantity)));
    }

    private boolean canChangeMind(OrderItemResponse item, boolean unopened) {
        return item.getReturnPolicySnapshot() != null && item.getReturnPolicySnapshot().allowsDiscretionaryReturn(unopened);
    }

    private AdminReturnDetail.Handling getHandling(UUID returnId, ReturnStatus status) {
        return handlingRepository.findByReturnIdAndNextStatus(returnId, status)
                .map(handling -> new AdminReturnDetail.Handling(handling.getNote(), handling.getCarrier(),
                        handling.getTrackingNumber(), handling.getItems().stream()
                                .map(line -> new AdminReturnDetail.Line(line.orderItemId(), line.quantity(),
                                        line.restock(), line.damaged())).toList()))
                .orElse(null);
    }

    private ReturnResponse toResponse(OrderReturn entry) {
        return new ReturnResponse(entry.getId(), entry.getOrderId(), entry.getStatus(), entry.getReason(),
                entry.getCustomerNote(), entry.getCreatedAt(), entry.getLines().stream()
                        .map(line -> new CreateReturnRequest.Item(line.orderItemId(), line.quantity())).toList());
    }
}
