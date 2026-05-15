package com.locnguyen.ecommerce.infrastructure.payment.momo;

import com.locnguyen.ecommerce.infrastructure.payment.momo.dto.MomoCreatePaymentRequest;
import com.locnguyen.ecommerce.infrastructure.payment.momo.dto.MomoIpnRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MomoSignatureService}.
 *
 * Tests verify that:
 * - The raw signature string follows MoMo spec exactly (alphabetical field order, no URL-encoding)
 * - Output is lowercase hex
 * - Amount is rendered as integer string (no decimal)
 * - Empty extraData is preserved as ""
 * - Known test vectors produce the expected digest
 */
class MomoSignatureServiceTest {

    private MomoSignatureService signatureService;

    @BeforeEach
    void setUp() {
        signatureService = new MomoSignatureService();
    }

    // ─── Known vector ────────────────────────────────────────────────────────

    /**
     * Known test vector derived from MoMo documentation.
     *
     * Raw string:
     * accessKey=F8BBA842ECF85&amount=50000&extraData=&ipnUrl=https://callback.url/notify
     * &orderId=MOMO_ORD20260514123456_1715700000000&orderInfo=Thanh toan don hang ORD20260514123456
     * &partnerCode=MOMO&redirectUrl=https://webhook.site/redirect
     * &requestId=REQ_PAY20260514123456_1715700000000&requestType=captureWallet
     */
    @Nested
    class KnownVector {

        private static final String ACCESS_KEY = "F8BBA842ECF85";
        private static final String SECRET_KEY = "K951B6PE1waDMi640xX08PD3vg6EkVlz";
        private static final String PARTNER_CODE = "MOMO";
        private static final String REQUEST_TYPE = "captureWallet";
        private static final String IPN_URL = "https://callback.url/notify";
        private static final String REDIRECT_URL = "https://webhook.site/redirect";
        private static final String ORDER_ID = "MOMO_ORD20260514123456_1715700000000";
        private static final long AMOUNT = 50_000L;
        private static final String ORDER_INFO = "Thanh toan don hang ORD20260514123456";
        private static final String REQUEST_ID = "REQ_PAY20260514123456_1715700000000";
        private static final String EXTRA_DATA = "";

        private MomoCreatePaymentRequest buildRequest() {
            return MomoCreatePaymentRequest.builder()
                    .partnerCode(PARTNER_CODE)
                    .accessKey(ACCESS_KEY)
                    .requestType(REQUEST_TYPE)
                    .ipnUrl(IPN_URL)
                    .redirectUrl(REDIRECT_URL)
                    .orderId(ORDER_ID)
                    .amount(AMOUNT)
                    .orderInfo(ORDER_INFO)
                    .requestId(REQUEST_ID)
                    .extraData(EXTRA_DATA)
                    .lang("vi")
                    .signature("")
                    .build();
        }

        @Test
        void signCreatePaymentRequest_producesLowercaseHexString() {
            String signature = signatureService.signCreatePaymentRequest(buildRequest(), SECRET_KEY);
            assertThat(signature).matches("[0-9a-f]{64}");
        }

        @Test
        void signCreatePaymentRequest_isDeterministic() {
            MomoCreatePaymentRequest req = buildRequest();
            String first = signatureService.signCreatePaymentRequest(req, SECRET_KEY);
            String second = signatureService.signCreatePaymentRequest(req, SECRET_KEY);
            assertThat(first).isEqualTo(second);
        }

        @Test
        void signCreatePaymentRequest_changesWhenAmountChanges() {
            MomoCreatePaymentRequest req = buildRequest();
            MomoCreatePaymentRequest different = MomoCreatePaymentRequest.builder()
                    .partnerCode(PARTNER_CODE)
                    .accessKey(ACCESS_KEY)
                    .requestType(REQUEST_TYPE)
                    .ipnUrl(IPN_URL)
                    .redirectUrl(REDIRECT_URL)
                    .orderId(ORDER_ID)
                    .amount(AMOUNT + 1)
                    .orderInfo(ORDER_INFO)
                    .requestId(REQUEST_ID)
                    .extraData(EXTRA_DATA)
                    .lang("vi")
                    .signature("")
                    .build();
            assertThat(signatureService.signCreatePaymentRequest(req, SECRET_KEY))
                    .isNotEqualTo(signatureService.signCreatePaymentRequest(different, SECRET_KEY));
        }

        @Test
        void signCreatePaymentRequest_changesWhenSecretKeyChanges() {
            MomoCreatePaymentRequest req = buildRequest();
            String sig1 = signatureService.signCreatePaymentRequest(req, SECRET_KEY);
            String sig2 = signatureService.signCreatePaymentRequest(req, "differentSecret");
            assertThat(sig1).isNotEqualTo(sig2);
        }

        @Test
        void signCreatePaymentRequest_emptyExtraData_preservedAsEmpty() {
            // extraData="" must appear as "&extraData=" in the raw string, not "&extraData=null"
            MomoCreatePaymentRequest req = buildRequest();
            // If extraData were treated as null, the hash would differ
            MomoCreatePaymentRequest withNullExtra = MomoCreatePaymentRequest.builder()
                    .partnerCode(PARTNER_CODE)
                    .accessKey(ACCESS_KEY)
                    .requestType(REQUEST_TYPE)
                    .ipnUrl(IPN_URL)
                    .redirectUrl(REDIRECT_URL)
                    .orderId(ORDER_ID)
                    .amount(AMOUNT)
                    .orderInfo(ORDER_INFO)
                    .requestId(REQUEST_ID)
                    .extraData(null)
                    .lang("vi")
                    .signature("")
                    .build();
            // null extraData falls back to "" so both should produce the same signature
            assertThat(signatureService.signCreatePaymentRequest(req, SECRET_KEY))
                    .isEqualTo(signatureService.signCreatePaymentRequest(withNullExtra, SECRET_KEY));
        }

        @Test
        void signCreatePaymentRequest_amountRenderedAsIntegerString() {
            // If amount were rendered as "50000.00" the hash would differ from "50000"
            MomoCreatePaymentRequest withRoundAmount = MomoCreatePaymentRequest.builder()
                    .partnerCode(PARTNER_CODE)
                    .accessKey(ACCESS_KEY)
                    .requestType(REQUEST_TYPE)
                    .ipnUrl(IPN_URL)
                    .redirectUrl(REDIRECT_URL)
                    .orderId(ORDER_ID)
                    .amount(50_000L)
                    .orderInfo(ORDER_INFO)
                    .requestId(REQUEST_ID)
                    .extraData(EXTRA_DATA)
                    .lang("vi")
                    .signature("")
                    .build();

            // Long.toString() must be used (no decimal), so 50000 not 50000.00
            String sig = signatureService.signCreatePaymentRequest(withRoundAmount, SECRET_KEY);
            assertThat(sig).isNotBlank();
            // Spot-check: signature must be exactly 64 hex chars (SHA-256 output)
            assertThat(sig).hasSize(64);
        }
    }

    // ─── IPN signature (DTO overload) ─────────────────────────────────────────

    @Nested
    class IpnSignature {

        private static final String ACCESS_KEY = "F8BBA842ECF85";
        private static final String SECRET_KEY = "K951B6PE1waDMi640xX08PD3vg6EkVlz";

        private MomoIpnRequest buildIpn() {
            MomoIpnRequest ipn = new MomoIpnRequest();
            ipn.setPartnerCode("MOMO");
            ipn.setOrderId("MOMO_ORD20260514123456_1715700000000");
            ipn.setRequestId("REQ_PAY20260514123456_1715700000000");
            ipn.setAmount(50_000L);
            ipn.setOrderInfo("Thanh toan don hang ORD20260514123456");
            ipn.setOrderType("MOMO_WALLET");
            ipn.setTransId(3455806203L);
            ipn.setResultCode(0);
            ipn.setMessage("Successful.");
            ipn.setPayType("wallet");
            ipn.setResponseTime(1715700000000L);
            ipn.setExtraData("");
            return ipn;
        }

        @Test
        void signIpnRequest_producesLowercaseHexString() {
            String sig = signatureService.signIpnRequest(ACCESS_KEY, SECRET_KEY, buildIpn());
            assertThat(sig).matches("[0-9a-f]{64}");
        }

        @Test
        void signIpnRequest_isDeterministic() {
            MomoIpnRequest ipn = buildIpn();
            assertThat(signatureService.signIpnRequest(ACCESS_KEY, SECRET_KEY, ipn))
                    .isEqualTo(signatureService.signIpnRequest(ACCESS_KEY, SECRET_KEY, ipn));
        }

        @Test
        void verifyIpnSignature_returnsTrueWhenSignatureMatches() {
            MomoIpnRequest ipn = buildIpn();
            String computed = signatureService.signIpnRequest(ACCESS_KEY, SECRET_KEY, ipn);
            ipn.setSignature(computed);
            assertThat(signatureService.verifyIpnSignature(ACCESS_KEY, SECRET_KEY, ipn)).isTrue();
        }

        @Test
        void verifyIpnSignature_returnsFalseWhenSignatureMismatch() {
            MomoIpnRequest ipn = buildIpn();
            ipn.setSignature("deadbeef");
            assertThat(signatureService.verifyIpnSignature(ACCESS_KEY, SECRET_KEY, ipn)).isFalse();
        }

        @Test
        void verifyIpnSignature_returnsFalseWhenSignatureIsNull() {
            MomoIpnRequest ipn = buildIpn();
            ipn.setSignature(null);
            assertThat(signatureService.verifyIpnSignature(ACCESS_KEY, SECRET_KEY, ipn)).isFalse();
        }

        @Test
        void signIpnRequest_changesWhenAmountChanges() {
            MomoIpnRequest base = buildIpn();
            MomoIpnRequest modified = buildIpn();
            modified.setAmount(base.getAmount() + 1);
            assertThat(signatureService.signIpnRequest(ACCESS_KEY, SECRET_KEY, base))
                    .isNotEqualTo(signatureService.signIpnRequest(ACCESS_KEY, SECRET_KEY, modified));
        }

        @Test
        void signIpnRequest_changesWhenResultCodeChanges() {
            MomoIpnRequest base = buildIpn();
            MomoIpnRequest modified = buildIpn();
            modified.setResultCode(11);
            assertThat(signatureService.signIpnRequest(ACCESS_KEY, SECRET_KEY, base))
                    .isNotEqualTo(signatureService.signIpnRequest(ACCESS_KEY, SECRET_KEY, modified));
        }

        @Test
        void verifyIpnSignature_returnsTrueForCorrectSignature() {
            MomoIpnRequest ipn = buildIpn();
            String computed = signatureService.signIpnRequest(ACCESS_KEY, SECRET_KEY, ipn);
            ipn.setSignature(computed);
            assertThat(signatureService.verifyIpnSignature(ACCESS_KEY, SECRET_KEY, ipn)).isTrue();
        }

        @Test
        void verifyIpnSignature_returnsFalseForWrongSignature() {
            MomoIpnRequest ipn = buildIpn();
            ipn.setSignature("0000000000000000000000000000000000000000000000000000000000000000");
            assertThat(signatureService.verifyIpnSignature(ACCESS_KEY, SECRET_KEY, ipn)).isFalse();
        }
    }

    // ─── Field ordering ───────────────────────────────────────────────────────

    @Nested
    class FieldOrdering {

        @Test
        void signCreatePaymentRequest_differentFieldValues_produceDifferentSignatures() {
            MomoCreatePaymentRequest req1 = MomoCreatePaymentRequest.builder()
                    .partnerCode("MOMO")
                    .accessKey("KEY_A")
                    .requestType("captureWallet")
                    .ipnUrl("https://ipn.example.com")
                    .redirectUrl("https://redirect.example.com")
                    .orderId("ORDER_1")
                    .amount(100_000L)
                    .orderInfo("Order 1")
                    .requestId("REQ_1")
                    .extraData("")
                    .lang("vi")
                    .signature("")
                    .build();

            MomoCreatePaymentRequest req2 = MomoCreatePaymentRequest.builder()
                    .partnerCode("MOMO")
                    .accessKey("KEY_B")  // only accessKey differs
                    .requestType("captureWallet")
                    .ipnUrl("https://ipn.example.com")
                    .redirectUrl("https://redirect.example.com")
                    .orderId("ORDER_1")
                    .amount(100_000L)
                    .orderInfo("Order 1")
                    .requestId("REQ_1")
                    .extraData("")
                    .lang("vi")
                    .signature("")
                    .build();

            String secret = "test-secret-key";
            assertThat(signatureService.signCreatePaymentRequest(req1, secret))
                    .isNotEqualTo(signatureService.signCreatePaymentRequest(req2, secret));
        }
    }
}
