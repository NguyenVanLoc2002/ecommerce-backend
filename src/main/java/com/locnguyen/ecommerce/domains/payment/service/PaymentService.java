package com.locnguyen.ecommerce.domains.payment.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.dto.InitPaymentRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentCallbackRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentCaptureRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentFilter;
import com.locnguyen.ecommerce.domains.payment.dto.PaymentResponse;
import com.locnguyen.ecommerce.domains.payment.dto.TransactionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentResponse createCodPayment(Order order);

    PaymentResponse completeCodPayment(UUID orderId);

    PaymentResponse initiateOnlinePayment(UUID orderId, Customer customer,
                                          InitPaymentRequest request, String idempotencyKey);

    PaymentResponse captureOnlinePayment(UUID orderId, Customer customer, PaymentCaptureRequest request);

    PaymentResponse processCallback(PaymentCallbackRequest request);

    PaymentResponse getPaymentForCustomer(UUID orderId, Customer customer);

    PagedResponse<PaymentResponse> getPayments(PaymentFilter filter, Pageable pageable);

    PaymentResponse adminGetById(UUID paymentId);

    PaymentResponse adminGetByOrderId(UUID orderId);

    PaymentResponse adminGetByCode(String paymentCode);

    List<TransactionResponse> getTransactions(UUID paymentId);
}
