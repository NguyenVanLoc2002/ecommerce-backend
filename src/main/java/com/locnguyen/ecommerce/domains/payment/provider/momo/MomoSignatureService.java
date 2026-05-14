package com.locnguyen.ecommerce.domains.payment.provider.momo;

import com.locnguyen.ecommerce.domains.payment.provider.momo.dto.MomoCreatePaymentRequest;
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
     * Verifies an IPN (webhook) callback from MoMo.
     *
     * <p>IPN raw signature fields (alphabetical, MoMo spec):
     * <pre>
     *   accessKey=$accessKey&amp;amount=$amount&amp;extraData=$extraData&amp;message=$message
     *   &amp;orderId=$orderId&amp;orderInfo=$orderInfo&amp;orderType=$orderType
     *   &amp;partnerCode=$partnerCode&amp;payType=$payType&amp;requestId=$requestId
     *   &amp;responseTime=$responseTime&amp;resultCode=$resultCode&amp;transId=$transId
     * </pre>
     *
     * @param accessKey      MoMo access key from properties
     * @param secretKey      MoMo secret key — never log this value
     * @param amount         from IPN JSON
     * @param extraData      from IPN JSON
     * @param message        from IPN JSON
     * @param orderId        from IPN JSON
     * @param orderInfo      from IPN JSON
     * @param orderType      from IPN JSON
     * @param partnerCode    from IPN JSON
     * @param payType        from IPN JSON
     * @param requestId      from IPN JSON
     * @param responseTime   from IPN JSON
     * @param resultCode     from IPN JSON
     * @param transId        from IPN JSON
     * @param receivedSignature signature field from the IPN JSON payload
     * @return {@code true} if the computed signature matches
     */
    public boolean verifyIpnSignature(
            String accessKey, String secretKey,
            String amount, String extraData, String message,
            String orderId, String orderInfo, String orderType,
            String partnerCode, String payType, String requestId,
            String responseTime, String resultCode, String transId,
            String receivedSignature) {

        String rawSignature = "accessKey=" + nullToEmpty(accessKey)
                + "&amount=" + nullToEmpty(amount)
                + "&extraData=" + nullToEmpty(extraData)
                + "&message=" + nullToEmpty(message)
                + "&orderId=" + nullToEmpty(orderId)
                + "&orderInfo=" + nullToEmpty(orderInfo)
                + "&orderType=" + nullToEmpty(orderType)
                + "&partnerCode=" + nullToEmpty(partnerCode)
                + "&payType=" + nullToEmpty(payType)
                + "&requestId=" + nullToEmpty(requestId)
                + "&responseTime=" + nullToEmpty(responseTime)
                + "&resultCode=" + nullToEmpty(resultCode)
                + "&transId=" + nullToEmpty(transId);

        String computed = hmacSha256(rawSignature, secretKey);
        return computed.equals(receivedSignature);
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
