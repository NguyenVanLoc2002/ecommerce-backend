package com.locnguyen.ecommerce.infrastructure.payment.mock;

import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.payment.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentProviderTest {

    private MockPaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockPaymentProvider();
    }

    private static final String SUCCESS_PAYLOAD = """
            {
              "orderCode": "ORD-001",
              "providerTxnId": "MOCK-TXN-001",
              "status": "SUCCESS"
            }
            """;

    private static final String FAILED_PAYLOAD = """
            {
              "orderCode": "ORD-001",
              "providerTxnId": "MOCK-TXN-002",
              "status": "FAILED"
            }
            """;

    // ─── getProviderName ─────────────────────────────────────────────────────

    @Test
    void getProviderName_returnsMock() {
        assertThat(provider.getProviderName()).isEqualTo("MOCK");
    }

    // ─── verifySignature ─────────────────────────────────────────────────────

    @Nested
    class VerifySignature {

        @Test
        void alwaysReturnsTrue_withValidBodyAndSignature() {
            assertThat(provider.verifySignature(SUCCESS_PAYLOAD, "any-signature")).isTrue();
        }

        @Test
        void alwaysReturnsTrue_withNullSignature() {
            assertThat(provider.verifySignature(SUCCESS_PAYLOAD, null)).isTrue();
        }

        @Test
        void alwaysReturnsTrue_withNullBody() {
            assertThat(provider.verifySignature(null, null)).isTrue();
        }

        @Test
        void alwaysReturnsTrue_withBlankBody() {
            assertThat(provider.verifySignature("  ", "sig")).isTrue();
        }
    }

    // ─── isSuccess ───────────────────────────────────────────────────────────

    @Nested
    class IsSuccess {

        @Test
        void returnsTrue_whenStatusSuccess() {
            assertThat(provider.isSuccess(SUCCESS_PAYLOAD)).isTrue();
        }

        @Test
        void returnsTrue_whenStatusSuccessIgnoreCase_lowercase() {
            String payload = "{\"orderCode\":\"ORD-001\",\"providerTxnId\":\"TXN\",\"status\":\"success\"}";
            assertThat(provider.isSuccess(payload)).isTrue();
        }

        @Test
        void returnsTrue_whenStatusSuccessIgnoreCase_mixedCase() {
            String payload = "{\"orderCode\":\"ORD-001\",\"providerTxnId\":\"TXN\",\"status\":\"Success\"}";
            assertThat(provider.isSuccess(payload)).isTrue();
        }

        @Test
        void returnsFalse_whenStatusFailed() {
            assertThat(provider.isSuccess(FAILED_PAYLOAD)).isFalse();
        }

        @Test
        void returnsFalse_whenStatusFieldMissing() {
            String payload = "{\"orderCode\":\"ORD-001\",\"providerTxnId\":\"TXN-001\"}";
            assertThat(provider.isSuccess(payload)).isFalse();
        }

        @Test
        void returnsFalse_whenPayloadMalformed() {
            assertThat(provider.isSuccess("not-valid-json")).isFalse();
        }

        @Test
        void returnsFalse_whenPayloadBlank() {
            assertThat(provider.isSuccess("  ")).isFalse();
        }

        @Test
        void returnsFalse_whenPayloadNull() {
            assertThat(provider.isSuccess(null)).isFalse();
        }

        @Test
        void returnsFalse_whenPayloadIsEmptyObject() {
            assertThat(provider.isSuccess("{}")).isFalse();
        }
    }

    // ─── extractProviderTxnId ────────────────────────────────────────────────

    @Nested
    class ExtractProviderTxnId {

        @Test
        void returnsProviderTxnId_fromValidPayload() {
            assertThat(provider.extractProviderTxnId(SUCCESS_PAYLOAD)).isEqualTo("MOCK-TXN-001");
        }

        @Test
        void returnsProviderTxnId_fromFailedPayload() {
            assertThat(provider.extractProviderTxnId(FAILED_PAYLOAD)).isEqualTo("MOCK-TXN-002");
        }

        @Test
        void returnsNull_whenFieldMissing() {
            String payload = "{\"orderCode\":\"ORD-001\",\"status\":\"SUCCESS\"}";
            assertThat(provider.extractProviderTxnId(payload)).isNull();
        }

        @Test
        void returnsNull_whenPayloadMalformed() {
            assertThat(provider.extractProviderTxnId("not-valid-json")).isNull();
        }

        @Test
        void returnsNull_whenPayloadBlank() {
            assertThat(provider.extractProviderTxnId("")).isNull();
        }

        @Test
        void returnsNull_whenPayloadNull() {
            assertThat(provider.extractProviderTxnId(null)).isNull();
        }
    }

    // ─── extractOrderCode ────────────────────────────────────────────────────

    @Nested
    class ExtractOrderCode {

        @Test
        void returnsOrderCode_fromValidPayload() {
            assertThat(provider.extractOrderCode(SUCCESS_PAYLOAD)).isEqualTo("ORD-001");
        }

        @Test
        void returnsNull_whenFieldMissing() {
            String payload = "{\"providerTxnId\":\"TXN-001\",\"status\":\"SUCCESS\"}";
            assertThat(provider.extractOrderCode(payload)).isNull();
        }

        @Test
        void returnsNull_whenPayloadMalformed() {
            assertThat(provider.extractOrderCode("not-valid-json")).isNull();
        }

        @Test
        void returnsNull_whenPayloadBlank() {
            assertThat(provider.extractOrderCode("")).isNull();
        }

        @Test
        void returnsNull_whenPayloadNull() {
            assertThat(provider.extractOrderCode(null)).isNull();
        }

        @Test
        void ignoresExtraFieldsInPayload() {
            String payload = """
                    {
                      "orderCode": "ORD-999",
                      "providerTxnId": "TXN-999",
                      "status": "SUCCESS",
                      "extraField": "ignored"
                    }
                    """;
            assertThat(provider.extractOrderCode(payload)).isEqualTo("ORD-999");
        }
    }

    // ─── createPaymentUrl ────────────────────────────────────────────────────

    @Nested
    class CreatePaymentUrl {

        private Payment payment(String paymentCode) {
            Payment p = new Payment();
            p.setPaymentCode(paymentCode);
            return p;
        }

        private Order order(String orderCode) {
            Order o = new Order();
            o.setOrderCode(orderCode);
            return o;
        }

        @Test
        void returnsUrl_containingOrderCode() {
            String url = provider.createPaymentUrl(
                    payment("PAY-001"), order("ORD-001"),
                    "http://localhost:3000/result", "http://localhost:8080/api/v1/payments");
            assertThat(url).contains("orderCode=ORD-001");
        }

        @Test
        void returnsUrl_containingMockProviderTxnId() {
            String url = provider.createPaymentUrl(
                    payment("PAY-001"), order("ORD-001"),
                    "http://localhost:3000/result", "http://localhost:8080/api/v1/payments");
            assertThat(url).contains("providerTxnId=MOCK-PAY-001");
        }

        @Test
        void returnsUrl_withSuccessStatusByDefault() {
            String url = provider.createPaymentUrl(
                    payment("PAY-001"), order("ORD-001"),
                    "http://localhost:3000/result", "http://localhost:8080/api/v1/payments");
            assertThat(url).contains("status=SUCCESS");
        }

        @Test
        void returnsUrl_pointingToMockCompleteEndpoint() {
            String url = provider.createPaymentUrl(
                    payment("PAY-001"), order("ORD-001"),
                    "http://localhost:3000/result", "http://localhost:8080/api/v1/payments");
            assertThat(url).startsWith("http://localhost:8080/api/v1/payments/mock/complete");
        }

        @Test
        void usesReturnUrl_whenCallbackUrlIsNull() {
            String url = provider.createPaymentUrl(
                    payment("PAY-001"), order("ORD-001"),
                    "http://localhost:3000/result", null);
            assertThat(url).startsWith("http://localhost:3000/result/mock/complete");
        }
    }
}
