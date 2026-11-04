package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.config.PaymentProviderConfigResolver;
import com.locnguyen.ecommerce.domains.payment.config.PaypalResolvedPaymentConfig;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProvider;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCaptureResult;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCreateResult;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalAmount;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCapture;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCaptureOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCreateOrderRequest;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCreateOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalExperienceContext;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalPaymentSource;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalPaymentSourcePaypal;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalPurchaseUnit;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalWebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaypalPaymentProvider implements PaymentProvider {

    static final String PROVIDER_NAME = "PAYPAL";

    private static final String INTENT_CAPTURE = "CAPTURE";
    private static final String EVENT_CAPTURE_COMPLETED = "PAYMENT.CAPTURE.COMPLETED";

    private final PaymentProviderConfigResolver configResolver;
    private final PaypalClient paypalClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public PaymentProviderCreateResult createPayment(Payment payment, Order order,
                                                     String returnUrl, String callbackUrl) {
        PaypalResolvedPaymentConfig config = paypal();
        BigDecimal paypalAmount = resolvePaypalAmount(config, payment.getAmount(), order.getOrderCode());
        String effectiveReturnUrl = (returnUrl != null && !returnUrl.isBlank())
                ? returnUrl.trim()
                : config.returnUrl();

        PaypalCreateOrderRequest createRequest = buildCreateOrderRequest(
                config, order.getOrderCode(), paypalAmount, effectiveReturnUrl);

        log.info("Calling PayPal create-order: orderCode={} amount={} currency={}",
                order.getOrderCode(), paypalAmount, config.currency());

        PaypalCreateOrderResponse response = paypalClient.createOrder(createRequest, order.getOrderCode());
        String approvalUrl = response.findApprovalUrl()
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_FAILED,
                        "PayPal response did not contain an approval URL"));

        return PaymentProviderCreateResult.builder()
                .paymentUrl(approvalUrl)
                .providerOrderId(response.getId())
                .providerRequestId(response.getId())
                .message(response.getStatus())
                .providerAmount(paypalAmount)
                .providerCurrency(config.currency())
                .providerExchangeRate(config.conversionRateVndToUsd())
                .build();
    }

    @Override
    public String createPaymentUrl(Payment payment, Order order, String returnUrl, String callbackUrl) {
        return createPayment(payment, order, returnUrl, callbackUrl).getPaymentUrl();
    }

    @Override
    public Optional<PaymentProviderCaptureResult> capturePayment(Payment payment, String providerToken) {
        PaypalCaptureOrderResponse response = paypalClient.captureOrder(providerToken);
        PaypalCapture capture = response.firstCapture().orElse(null);

        if (capture == null) {
            return Optional.of(PaymentProviderCaptureResult.builder()
                    .success(false)
                    .status(response.getStatus())
                    .message("No capture in PayPal response")
                    .build());
        }

        boolean success = "COMPLETED".equals(capture.getStatus());
        return Optional.of(PaymentProviderCaptureResult.builder()
                .success(success)
                .providerTxnId(capture.getId())
                .status(capture.getStatus())
                .message(response.getStatus())
                .build());
    }

    @Override
    public boolean verifySignature(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("PayPal webhook received with null/blank signature headers JSON");
            return false;
        }

        Map<String, String> headers;
        try {
            headers = objectMapper.readValue(signature, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse PayPal webhook headers from signature param: {}", e.getMessage());
            return false;
        }

        return paypalClient.verifyWebhookSignature(
                headers.getOrDefault("paypal_auth_algo", ""),
                headers.getOrDefault("paypal_cert_url", ""),
                headers.getOrDefault("paypal_transmission_id", ""),
                headers.getOrDefault("paypal_transmission_sig", ""),
                headers.getOrDefault("paypal_transmission_time", ""),
                rawBody
        );
    }

    @Override
    public boolean isSuccess(String payload) {
        try {
            PaypalWebhookEvent event = objectMapper.readValue(payload, PaypalWebhookEvent.class);
            return EVENT_CAPTURE_COMPLETED.equals(event.getEventType())
                    && event.getResource() != null
                    && "COMPLETED".equals(event.getResource().getStatus());
        } catch (Exception e) {
            log.warn("Failed to parse PayPal webhook payload in isSuccess: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String extractProviderTxnId(String payload) {
        try {
            PaypalWebhookEvent event = objectMapper.readValue(payload, PaypalWebhookEvent.class);
            return event.getResource() != null ? event.getResource().getId() : null;
        } catch (Exception e) {
            log.warn("Failed to extract providerTxnId from PayPal webhook payload: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String extractOrderCode(String payload) {
        try {
            PaypalWebhookEvent event = objectMapper.readValue(payload, PaypalWebhookEvent.class);
            return event.getResource() != null ? event.getResource().getCustomId() : null;
        } catch (Exception e) {
            log.warn("Failed to extract orderCode from PayPal webhook payload: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public BigDecimal extractAmount(String payload) {
        return null;
    }

    private PaypalCreateOrderRequest buildCreateOrderRequest(
            PaypalResolvedPaymentConfig config, String orderCode, BigDecimal amount, String returnUrl) {
        return PaypalCreateOrderRequest.builder()
                .intent(INTENT_CAPTURE)
                .purchaseUnits(List.of(
                        PaypalPurchaseUnit.builder()
                                .referenceId(orderCode)
                                .customId(orderCode)
                                .amount(PaypalAmount.builder()
                                        .currencyCode(config.currency())
                                        .value(formatAmount(amount))
                                        .build())
                                .build()))
                .paymentSource(PaypalPaymentSource.builder()
                        .paypal(PaypalPaymentSourcePaypal.builder()
                                .experienceContext(PaypalExperienceContext.builder()
                                        .brandName(config.brandName())
                                        .locale(config.locale())
                                        .userAction(config.userAction())
                                        .paymentMethodPreference(config.paymentMethodPreference())
                                        .shippingPreference(config.shippingPreference())
                                        .returnUrl(returnUrl)
                                        .cancelUrl(config.cancelUrl())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private BigDecimal resolvePaypalAmount(PaypalResolvedPaymentConfig config,
                                           BigDecimal orderAmount, String orderCode) {
        if (!"USD".equalsIgnoreCase(config.currency())) {
            throw new AppException(ErrorCode.PAYMENT_CURRENCY_UNSUPPORTED,
                    "PayPal production payments support USD only");
        }

        BigDecimal rate = config.conversionRateVndToUsd();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.PAYMENT_CURRENCY_UNSUPPORTED,
                    "Invalid testConversionRateVndToUsd â€” must be > 0");
        }
        return orderAmount.divide(rate, 2, RoundingMode.HALF_UP);
    }

    private PaypalResolvedPaymentConfig paypal() {
        return configResolver.resolvePaypal();
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
