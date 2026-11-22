package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.service.impl.PaymentExpiryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentExpiryServiceTest {
    @Mock PaymentRepository paymentRepository;
    @InjectMocks PaymentExpiryServiceImpl service;

    @Test
    void expiresInitiatedPaymentButNeverOverwritesReceivedFunds() {
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setStatus(PaymentRecordStatus.INITIATED);
        when(paymentRepository.findByOrderIdWithLock(orderId)).thenReturn(Optional.of(payment));

        service.cancelUnpaidPayment(orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentRecordStatus.CANCELLED);
        verify(paymentRepository).save(payment);

        payment.setStatus(PaymentRecordStatus.PAID);
        service.cancelUnpaidPayment(orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentRecordStatus.PAID);
        verify(paymentRepository).save(payment);
    }
}
