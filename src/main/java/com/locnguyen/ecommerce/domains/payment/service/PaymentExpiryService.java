package com.locnguyen.ecommerce.domains.payment.service;

import java.util.UUID;

public interface PaymentExpiryService {
    void cancelUnpaidPayment(UUID orderId);
}
