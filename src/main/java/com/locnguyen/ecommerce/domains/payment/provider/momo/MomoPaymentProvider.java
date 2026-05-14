package com.locnguyen.ecommerce.domains.payment.provider.momo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProvider;
import com.locnguyen.ecommerce.domains.payment.provider.PaymentProviderCreateResult;
import com.locnguyen.ecommerce.domains.payment.provider.momo.dto.MomoCreatePaymentRequest;
import com.locnguyen.ecommerce.domains.payment.provider.momo.dto.MomoCreatePaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URI;

/**
 * MoMo wallet one-time payment provider ({@code captureWallet} flow).
 *
 * <p>Active only when {@code app.payment.momo.enabled=true}.
 *
 * <p>Session 1 scope: create-payment URL generation.
 * IPN/webhook state mutation is implemented in Session 2.
 *
 * <p>Never log {@code secretKey}, {@code accessKey}, or the raw signature string.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.momo.enabled", havingValue = "true")
public class MomoPaymentProvider implements PaymentProvider {

    static final String PROVIDER_NAME = "MOMO";

    private static final long MOMO_MIN_AMOUNT = 1_000L;
    private static final long MOMO_MAX_AMOUNT = 50_000_000L;
    private static final int MOMO_SUCCESS_CODE = 0;

    private final MomoPaymentProperties properties;
    private final MomoSignatureService signatureService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public MomoPaymentProvider(
            MomoPaymentProperties properties,
            MomoSignatureService signatureService,
            ObjectMapper objectMapper,
            @Qualifier("momoRestClient") RestClient restClient) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * Verifies the HMAC-SHA256 signature of an inbound MoMo IPN webhook.
     *
     * <p>This method extracts the required fields from the raw JSON body,
     * recomputes the signature, and compares it to the received one.
     * Returns {@code false} if the body is malformed or if signature verification fails.
     */
    @Override
    public boolean verifySignature(String rawBody, String signature) {
        if (rawBody == null || rawBody.isBlank() || signature == null) {
            return false;
        }
        try {
            JsonNode json = objectMapper.readTree(rawBody);
            return signatureService.verifyIpnSignature(
                    properties.getAccessKey(),
                    properties.getSecretKey(),
                    text(json, "amount"),
                    text(json, "extraData"),
                    text(json, "message"),
                    text(json, "orderId"),
                    text(json, "orderInfo"),
                    text(json, "orderType"),
                    text(json, "partnerCode"),
                    text(json, "payType"),
                    text(json, "requestId"),
                    text(json, "responseTime"),
                    text(json, "resultCode"),
                    text(json, "transId"),
                    signature
            );
        } catch (Exception e) {
            log.warn("MoMo IPN signature verification failed — could not parse payload: {}", e.getMessage());
            return false;
        }
    }

    /** Returns {@code true} when the IPN payload's {@code resultCode} is 0. */
    @Override
    public boolean isSuccess(String payload) {
        if (payload == null || payload.isBlank()) return false;
        try {
            JsonNode json = objectMapper.readTree(payload);
            JsonNode resultCode = json.get("resultCode");
            return resultCode != null && resultCode.asInt(-1) == MOMO_SUCCESS_CODE;
        } catch (Exception e) {
            log.debug("MoMo isSuccess: failed to parse payload — {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts MoMo's own transaction ID ({@code transId}) from the IPN payload.
     * This is used as the {@code providerTxnId} in the payment audit trail.
     */
    @Override
    public String extractProviderTxnId(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            JsonNode json = objectMapper.readTree(payload);
            JsonNode transId = json.get("transId");
            return (transId != null && !transId.isNull()) ? transId.asText() : null;
        } catch (Exception e) {
            log.debug("MoMo extractProviderTxnId: failed to parse payload — {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the internal order code from the MoMo IPN payload.
     *
     * <p>MoMo's {@code orderId} field holds the provider order id we generated
     * at create-payment time in the format {@code MOMO_{orderCode}_{timestamp}}.
     * This method strips the prefix and suffix to recover the {@code orderCode}.
     */
    @Override
    public String extractOrderCode(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            JsonNode json = objectMapper.readTree(payload);
            JsonNode orderIdNode = json.get("orderId");
            if (orderIdNode == null || orderIdNode.isNull()) return null;
            return parseOrderCodeFromProviderOrderId(orderIdNode.asText());
        } catch (Exception e) {
            log.debug("MoMo extractOrderCode: failed to parse payload — {}", e.getMessage());
            return null;
        }
    }

    /**
     * Delegates to {@link #createPayment} and returns the primary web redirect URL.
     * Use {@link #createPayment} directly when you also need deeplink or QR data.
     */
    @Override
    public String createPaymentUrl(Payment payment, Order order, String returnUrl, String callbackUrl) {
        return createPayment(payment, order, returnUrl, callbackUrl).getPaymentUrl();
    }

    /**
     * Calls the MoMo create-payment API and returns the full provider result.
     *
     * <p>Validates amount bounds before calling the API.
     * Throws {@link AppException} with {@link ErrorCode#PAYMENT_FAILED} if:
     * <ul>
     *   <li>Amount is outside MoMo's allowed range (1 000 – 50 000 000 VND)</li>
     *   <li>HTTP call fails or times out</li>
     *   <li>MoMo returns {@code resultCode != 0}</li>
     * </ul>
     *
     * <p>On success ({@code resultCode == 0}), returns a result with
     * {@code paymentUrl}, {@code deeplink}, {@code qrCodeUrl},
     * {@code providerOrderId}, and {@code providerRequestId}.
     * Payment status is NOT changed to PAID here — that happens on IPN.
     */
    @Override
    public PaymentProviderCreateResult createPayment(Payment payment, Order order,
                                                     String returnUrl, String callbackUrl) {
        long amount = toMomoAmount(payment.getAmount());
        validateAmount(amount, order.getOrderCode());

        String providerOrderId = buildProviderOrderId(order.getOrderCode());
        String requestId = buildRequestId(payment.getPaymentCode());
        String orderInfo = "Thanh toan don hang " + order.getOrderCode();

        MomoCreatePaymentRequest requestBody = MomoCreatePaymentRequest.builder()
                .partnerCode(properties.getPartnerCode())
                .accessKey(properties.getAccessKey())
                .requestType(properties.getRequestType())
                .ipnUrl(callbackUrl != null ? callbackUrl : properties.getIpnUrl())
                .redirectUrl(returnUrl != null ? returnUrl : properties.getRedirectUrl())
                .orderId(providerOrderId)
                .amount(amount)
                .orderInfo(orderInfo)
                .requestId(requestId)
                .extraData("")
                .lang(properties.getLang())
                .signature("") // placeholder — replaced after signing below
                .build();

        String signature = signatureService.signCreatePaymentRequest(requestBody, properties.getSecretKey());

        requestBody = MomoCreatePaymentRequest.builder()
                .partnerCode(requestBody.getPartnerCode())
                .accessKey(requestBody.getAccessKey())
                .requestType(requestBody.getRequestType())
                .ipnUrl(requestBody.getIpnUrl())
                .redirectUrl(requestBody.getRedirectUrl())
                .orderId(requestBody.getOrderId())
                .amount(requestBody.getAmount())
                .orderInfo(requestBody.getOrderInfo())
                .requestId(requestBody.getRequestId())
                .extraData(requestBody.getExtraData())
                .lang(requestBody.getLang())
                .signature(signature)
                .build();

        log.info("Calling MoMo create-payment API: providerOrderId={} requestId={} amount={}",
                providerOrderId, requestId, amount);

        MomoCreatePaymentResponse momoResponse = callMomoCreateApi(requestBody, order.getOrderCode());

        if (momoResponse.getResultCode() != MOMO_SUCCESS_CODE) {
            log.warn("MoMo create-payment rejected: providerOrderId={} resultCode={} message={}",
                    providerOrderId, momoResponse.getResultCode(), momoResponse.getMessage());
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "MoMo rejected payment creation: " + momoResponse.getMessage());
        }

        log.info("MoMo create-payment succeeded: providerOrderId={} requestId={}",
                providerOrderId, requestId);

        return PaymentProviderCreateResult.builder()
                .paymentUrl(momoResponse.getPayUrl())
                .deeplink(momoResponse.getDeeplink())
                .qrCodeUrl(momoResponse.getQrCodeUrl())
                .providerOrderId(providerOrderId)
                .providerRequestId(requestId)
                .resultCode(momoResponse.getResultCode())
                .message(momoResponse.getMessage())
                .build();
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private MomoCreatePaymentResponse callMomoCreateApi(MomoCreatePaymentRequest requestBody,
                                                        String orderCode) {
        try {
            MomoCreatePaymentResponse response = restClient.post()
                    .uri(URI.create(properties.getCreateUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(MomoCreatePaymentResponse.class);

            if (response == null) {
                throw new AppException(ErrorCode.PAYMENT_FAILED,
                        "MoMo API returned empty response");
            }
            return response;
        } catch (AppException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("MoMo API call failed: orderCode={} error={}", orderCode, e.getMessage());
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "MoMo API call failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling MoMo API: orderCode={} error={}", orderCode, e.getMessage(), e);
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "Unexpected error calling MoMo API");
        }
    }

    private void validateAmount(long amount, String orderCode) {
        if (amount < MOMO_MIN_AMOUNT || amount > MOMO_MAX_AMOUNT) {
            log.warn("MoMo payment amount out of range: orderCode={} amount={}", orderCode, amount);
            throw new AppException(ErrorCode.PAYMENT_FAILED,
                    "Payment amount must be between " + MOMO_MIN_AMOUNT
                    + " and " + MOMO_MAX_AMOUNT + " VND");
        }
    }

    /**
     * Builds the MoMo provider orderId from the internal order code.
     *
     * <p>Format: {@code MOMO_{orderCode}_{currentTimeMillis}}
     * The timestamp ensures uniqueness across retries for the same order.
     * The orderCode can be recovered via {@link #parseOrderCodeFromProviderOrderId}.
     */
    private String buildProviderOrderId(String orderCode) {
        return "MOMO_" + orderCode + "_" + System.currentTimeMillis();
    }

    /**
     * Builds a unique MoMo requestId from the internal payment code.
     *
     * <p>Format: {@code REQ_{paymentCode}_{currentTimeMillis}}
     */
    private String buildRequestId(String paymentCode) {
        return "REQ_" + paymentCode + "_" + System.currentTimeMillis();
    }

    /**
     * Parses the internal orderCode from a provider orderId.
     *
     * <p>Given {@code MOMO_ORD20260514123456_1715700000000},
     * returns {@code ORD20260514123456}.
     */
    static String parseOrderCodeFromProviderOrderId(String providerOrderId) {
        if (providerOrderId == null) return null;
        String withoutPrefix = providerOrderId.startsWith("MOMO_")
                ? providerOrderId.substring(5)
                : providerOrderId;
        int lastUnderscore = withoutPrefix.lastIndexOf('_');
        return lastUnderscore > 0
                ? withoutPrefix.substring(0, lastUnderscore)
                : withoutPrefix;
    }

    private static long toMomoAmount(BigDecimal amount) {
        if (amount == null) return 0L;
        return amount.longValue();
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return (node == null || node.isNull()) ? "" : node.asText("");
    }
}
