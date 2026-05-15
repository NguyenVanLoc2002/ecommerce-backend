package com.locnguyen.ecommerce.infrastructure.payment.momo;

import com.locnguyen.ecommerce.infrastructure.payment.momo.dto.MomoCreatePaymentRequest;
import com.locnguyen.ecommerce.infrastructure.payment.momo.dto.MomoIpnRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Computes MoMo HMAC-SHA256 signatures for create-payment requests.
 *
 * <p>The raw signature string is constructed exactly as specified in MoMo documentation:
 * <pre>
 *   accessKey=$accessKey&amp;amount=$amount&amp;extraData=$extraData&amp;ipnUrl=$ipnUrl
 *   &amp;orderId=$orderId&amp;orderInfo=$orderInfo&amp;partnerCode=$partnerCode
 *   &amp;redirectUrl=$redirectUrl&amp;requestId=$requestId&amp;requestType=$requestType
 * </pre>
 *
 * <p>The secret key is never logged and is not included in the raw string.
 */
@Component
public class MomoSignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Signs a create-payment request and returns a lowercase hex HMAC-SHA256 string.
     *
     * <p>The field order in the raw signature string is alphabetical and fixed by MoMo spec.
     * Do NOT sort dynamically at runtime — any ordering change breaks signature verification.
     *
     * @param request   populated create-payment request (must have all fields set before signing)
     * @param secretKey MoMo secret key — never log this value
     * @return lowercase hex HMAC-SHA256 signature
     */
    public String signCreatePaymentRequest(MomoCreatePaymentRequest request, String secretKey) {
        String rawSignature = buildCreatePaymentRawSignature(request);
        return hmacSha256(rawSignature, secretKey);
    }

    /**
     * Computes the HMAC-SHA256 signature for an IPN callback.
     *
     * <p>MoMo signs IPN callbacks using a different field set than create-payment requests.
     * Fields are in strict alphabetical order per MoMo spec:
     * <pre>
     *   accessKey=$accessKey&amp;amount=$amount&amp;extraData=$extraData&amp;message=$message
     *   &amp;orderId=$orderId&amp;orderInfo=$orderInfo&amp;orderType=$orderType
     *   &amp;partnerCode=$partnerCode&amp;payType=$payType&amp;requestId=$requestId
     *   &amp;responseTime=$responseTime&amp;resultCode=$resultCode&amp;transId=$transId
     * </pre>
     *
     * @param accessKey  our MoMo access key — never log this value
     * @param secretKey  our MoMo secret key — never log this value
     * @param request    deserialized IPN payload (the {@code signature} field is excluded from computation)
     * @return lowercase hex HMAC-SHA256 signature string
     */
    public String signIpnRequest(String accessKey, String secretKey, MomoIpnRequest request) {
        String rawSignature = "accessKey=" + nullToEmpty(accessKey)
                + "&amount=" + (request.getAmount() != null ? request.getAmount() : 0L)
                + "&extraData=" + nullToEmpty(request.getExtraData())
                + "&message=" + nullToEmpty(request.getMessage())
                + "&orderId=" + nullToEmpty(request.getOrderId())
                + "&orderInfo=" + nullToEmpty(request.getOrderInfo())
                + "&orderType=" + nullToEmpty(request.getOrderType())
                + "&partnerCode=" + nullToEmpty(request.getPartnerCode())
                + "&payType=" + nullToEmpty(request.getPayType())
                + "&requestId=" + nullToEmpty(request.getRequestId())
                + "&responseTime=" + (request.getResponseTime() != null ? request.getResponseTime() : 0L)
                + "&resultCode=" + (request.getResultCode() != null ? request.getResultCode() : 0)
                + "&transId=" + (request.getTransId() != null ? request.getTransId() : 0L);
        return hmacSha256(rawSignature, secretKey);
    }

    /**
     * Convenience overload that accepts an already-deserialized {@link MomoIpnRequest}.
     *
     * @param accessKey  our MoMo access key — never log this value
     * @param secretKey  our MoMo secret key — never log this value
     * @param request    deserialized IPN payload; {@code request.getSignature()} is the received digest
     * @return {@code true} if the computed HMAC matches {@code request.getSignature()}
     */
    public boolean verifyIpnSignature(String accessKey, String secretKey, MomoIpnRequest request) {
        String computed = signIpnRequest(accessKey, secretKey, request);
        return computed.equals(nullToEmpty(request.getSignature()));
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private String buildCreatePaymentRawSignature(MomoCreatePaymentRequest request) {
        return "accessKey=" + nullToEmpty(request.getAccessKey())
                + "&amount=" + request.getAmount()
                + "&extraData=" + nullToEmpty(request.getExtraData())
                + "&ipnUrl=" + nullToEmpty(request.getIpnUrl())
                + "&orderId=" + nullToEmpty(request.getOrderId())
                + "&orderInfo=" + nullToEmpty(request.getOrderInfo())
                + "&partnerCode=" + nullToEmpty(request.getPartnerCode())
                + "&redirectUrl=" + nullToEmpty(request.getRedirectUrl())
                + "&requestId=" + nullToEmpty(request.getRequestId())
                + "&requestType=" + nullToEmpty(request.getRequestType());
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
