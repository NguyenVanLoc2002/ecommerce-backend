package com.locnguyen.ecommerce.domains.payment.service.impl;

import com.locnguyen.ecommerce.domains.payment.enums.PaymentRecordStatus;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentRepository;
import com.locnguyen.ecommerce.domains.payment.service.PaymentExpiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentExpiryServiceImpl implements PaymentExpiryService {
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public void cancelUnpaidPayment(UUID orderId) {
        paymentRepository.findByOrderIdWithLock(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentRecordStatus.PENDING
                    || payment.getStatus() == PaymentRecordStatus.INITIATED
                    || payment.getStatus() == PaymentRecordStatus.FAILED) {
                payment.setStatus(PaymentRecordStatus.CANCELLED);
                paymentRepository.save(payment);
            }
        });
    }
}
