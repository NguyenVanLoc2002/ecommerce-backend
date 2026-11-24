package com.locnguyen.ecommerce.domains.returns.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.dto.ReturnFinancialLine;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.payment.dto.RefundResponse;
import com.locnguyen.ecommerce.domains.payment.dto.ReturnRefundAllocation;
import com.locnguyen.ecommerce.domains.payment.service.PaymentRefundService;
import com.locnguyen.ecommerce.domains.returns.enums.ExchangeStatus;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.repository.OrderReturnRepository;
import com.locnguyen.ecommerce.domains.returns.repository.ReturnExchangeRepository;
import com.locnguyen.ecommerce.domains.returns.repository.ReturnHandlingRepository;
import com.locnguyen.ecommerce.domains.returns.service.ReturnEligibility;
import com.locnguyen.ecommerce.domains.returns.service.ReturnRefundService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReturnRefundServiceImpl implements ReturnRefundService {
    private final OrderService orderService;
    private final OrderReturnRepository returnRepository;
    private final ReturnHandlingRepository handlingRepository;
    private final ReturnExchangeRepository exchangeRepository;
    private final PaymentRefundService refundService;

    @Override
    @Transactional
    public RefundResponse prepare(UUID orderId, UUID returnId) {
        orderService.lockOrderForReturn(orderId);
        var entry = returnRepository.findById(returnId).filter(row -> row.getOrderId().equals(orderId))
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_NOT_FOUND));
        var priorRefund = refundService.findReturnRefund(returnId);
        if (priorRefund.isPresent()) {
            return priorRefund.get();
        }
        if (entry.getStatus() != ReturnStatus.INSPECTED) {
            throw new AppException(ErrorCode.RETURN_DECISION_INVALID, "Inspection is required before preparing a refund");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (exchangeRepository.findByReturnId(returnId).stream().anyMatch(row ->
                row.getStatus() == ExchangeStatus.REPLACEMENT_CREATED
                        || (row.getStatus() == ExchangeStatus.RESERVED && row.getExpiresAt().isAfter(now)))) {
            throw new AppException(ErrorCode.RETURN_DECISION_INVALID, "Resolve the exchange before preparing a refund");
        }

        var previous = refundService.getReservedReturnAllocations(orderId);
        var consumed = previous.stream().collect(Collectors.groupingBy(ReturnRefundAllocation::orderItemId,
                Collectors.summingLong(ReturnRefundAllocation::quantity)));
        var items = orderService.getReturnFinancialLines(orderId).stream()
                .collect(Collectors.toMap(ReturnFinancialLine::id, Function.identity()));
        var inspection = handlingRepository.findByReturnIdAndNextStatus(returnId, ReturnStatus.INSPECTED)
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_DECISION_INVALID));
        var allocations = new ArrayList<ReturnRefundAllocation>();
        for (var inspected : inspection.getItems()) {
            int quantity = entry.getReason() == ReturnReason.CHANGED_MIND ? inspected.restock() : inspected.quantity();
            if (quantity == 0) {
                continue;
            }
            var line = items.get(inspected.orderItemId());
            if (line == null || line.netLineAmount() == null || line.netLineAmount().signum() < 0
                    || line.netLineAmount().stripTrailingZeros().scale() > 0) {
                throw new AppException(ErrorCode.RETURN_REFUND_REVIEW_REQUIRED);
            }
            long alreadyRefunded = consumed.getOrDefault(line.id(), 0L);
            ReturnEligibility.requireAvailableQuantity(line.quantity(), alreadyRefunded, quantity);
            BigDecimal total = line.netLineAmount();
            BigDecimal units = BigDecimal.valueOf(line.quantity());
            BigDecimal previousAmount = total.multiply(BigDecimal.valueOf(alreadyRefunded))
                    .divide(units, 0, RoundingMode.DOWN);
            BigDecimal nextAmount = total.multiply(BigDecimal.valueOf(alreadyRefunded + quantity))
                    .divide(units, 0, RoundingMode.DOWN);
            allocations.add(new ReturnRefundAllocation(line.id(), quantity, nextAmount.subtract(previousAmount)));
        }
        boolean fullOrder = allocations.size() == items.size() && allocations.stream()
                .allMatch(line -> line.quantity() == items.get(line.orderItemId()).quantity());
        BigDecimal shipping = entry.getReason() != ReturnReason.CHANGED_MIND && fullOrder
                ? orderService.getOrderByIdAdmin(orderId).getShippingFee() : BigDecimal.ZERO;
        return refundService.initiateReturnRefund(orderId, returnId, allocations, shipping);
    }
}
