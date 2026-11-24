package com.locnguyen.ecommerce.domains.returns;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.dto.ReturnFinancialLine;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.payment.dto.ReturnRefundAllocation;
import com.locnguyen.ecommerce.domains.payment.service.PaymentRefundService;
import com.locnguyen.ecommerce.domains.returns.entity.OrderReturn;
import com.locnguyen.ecommerce.domains.returns.entity.ReturnHandling;
import com.locnguyen.ecommerce.domains.returns.entity.ReturnHandlingLine;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.repository.OrderReturnRepository;
import com.locnguyen.ecommerce.domains.returns.repository.ReturnExchangeRepository;
import com.locnguyen.ecommerce.domains.returns.repository.ReturnHandlingRepository;
import com.locnguyen.ecommerce.domains.returns.service.impl.ReturnRefundServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnRefundServiceTest {
    private final UUID orderId = UUID.randomUUID();
    private final UUID returnId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @Mock private OrderService orderService;
    @Mock private OrderReturnRepository returnRepository;
    @Mock private ReturnHandlingRepository handlingRepository;
    @Mock private ReturnExchangeRepository exchangeRepository;
    @Mock private PaymentRefundService paymentRefundService;
    @InjectMocks private ReturnRefundServiceImpl service;

    @BeforeEach
    void inspectedReturn() {
        OrderReturn entry = new OrderReturn();
        entry.setOrderId(orderId);
        entry.setReason(ReturnReason.DEFECTIVE);
        entry.setStatus(ReturnStatus.INSPECTED);
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(entry));
        ReturnHandling inspection = new ReturnHandling();
        inspection.getItems().add(new ReturnHandlingLine(itemId, 1, 0, 1));
        when(handlingRepository.findByReturnIdAndNextStatus(returnId, ReturnStatus.INSPECTED))
                .thenReturn(Optional.of(inspection));
    }

    @Test
    void prepare_assignsRemainingRoundingUnitToSecondPartialReturn() {
        when(orderService.getReturnFinancialLines(orderId))
                .thenReturn(List.of(new ReturnFinancialLine(itemId, 2, new BigDecimal("199999"))));
        when(paymentRefundService.getReservedReturnAllocations(orderId))
                .thenReturn(List.of(new ReturnRefundAllocation(itemId, 1, new BigDecimal("99999"))));

        service.prepare(orderId, returnId);

        verify(paymentRefundService).initiateReturnRefund(orderId, returnId,
                List.of(new ReturnRefundAllocation(itemId, 1, new BigDecimal("100000"))), BigDecimal.ZERO);
    }

    @Test
    void prepare_requiresReconciliationWhenHistoricalNetAmountIsMissing() {
        when(orderService.getReturnFinancialLines(orderId))
                .thenReturn(List.of(new ReturnFinancialLine(itemId, 2, null)));

        assertThatThrownBy(() -> service.prepare(orderId, returnId)).isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RETURN_REFUND_REVIEW_REQUIRED);
    }
}
