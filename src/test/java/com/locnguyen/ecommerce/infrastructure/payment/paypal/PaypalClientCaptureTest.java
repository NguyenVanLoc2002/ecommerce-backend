package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.payment.config.PaymentProviderConfigResolver;
import com.locnguyen.ecommerce.domains.payment.config.PaypalResolvedPaymentConfig;
import com.locnguyen.ecommerce.infrastructure.payment.PaymentRestClientFactory;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalCaptureOrderResponse;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalWebhookVerifyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaypalClientCaptureTest {

    @Mock
    private PaymentProviderConfigResolver configResolver;
    @Mock
    private PaypalOAuthClient oAuthClient;
    @Mock
    private PaymentRestClientFactory restClientFactory;
    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec uriSpec;
    @Mock
    private RestClient.RequestBodySpec bodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PaypalResolvedPaymentConfig paypalConfig;
    private PaypalClient paypalClient;

    @BeforeEach
    void setUp() {
        paypalConfig = new PaypalResolvedPaymentConfig(
                true,
                "SANDBOX",
                "test-client-id",
                "test-secret",
                "https://api-m.sandbox.paypal.com",
                "http://localhost:5173/payment/paypal/return",
                "http://localhost:5173/payment/paypal/cancel",
                "WH-12345",
                "USD",
                "Locen Studio",
                "en-US",
                "PAY_NOW",
                "IMMEDIATE_PAYMENT_REQUIRED",
                "NO_SHIPPING",
                new BigDecimal("25000"),
                30_000,
                30_000
        );

        when(configResolver.resolvePaypal()).thenReturn(paypalConfig);
        when(oAuthClient.getAccessToken()).thenReturn("test-access-token");
        when(restClientFactory.create(anyInt(), anyInt())).thenReturn(restClient);

        doReturn(uriSpec).when(restClient).post();
        doReturn(bodySpec).when(uriSpec).uri(any(String.class));
        doReturn(bodySpec).when(bodySpec).header(any(), any());
        doReturn(bodySpec).when(bodySpec).contentType(any(MediaType.class));
        doReturn(bodySpec).when(bodySpec).body(any(Object.class));
        doReturn(responseSpec).when(bodySpec).retrieve();

        paypalClient = new PaypalClient(configResolver, oAuthClient, restClientFactory, objectMapper);
    }

    private PaypalCaptureOrderResponse emptyCaptureResponse() {
        return new PaypalCaptureOrderResponse();
    }

    private PaypalWebhookVerifyResponse verifyResponse(String status) {
        PaypalWebhookVerifyResponse response = new PaypalWebhookVerifyResponse();
        setField(response, "verificationStatus", status);
        return response;
    }

    @Nested
    class CaptureOrder {

        @Test
        void returnsResponse_whenPaypalResponds() {
            PaypalCaptureOrderResponse expected = emptyCaptureResponse();
            when(responseSpec.body(PaypalCaptureOrderResponse.class)).thenReturn(expected);

            PaypalCaptureOrderResponse result = paypalClient.captureOrder("PAYPAL_ORDER_001");

            assertThat(result).isSameAs(expected);
            verify(restClientFactory).create(30_000, 30_000);
        }

        @Test
        void throwsPaymentFailed_whenResponseIsNull() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class)).thenReturn(null);

            assertThatThrownBy(() -> paypalClient.captureOrder("PAYPAL_ORDER_001"))
                    .isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void throwsPaymentFailed_onRestClientException() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class))
                    .thenThrow(new RestClientException("Connection refused"));

            assertThatThrownBy(() -> paypalClient.captureOrder("PAYPAL_ORDER_001"))
                    .isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void rethrowsAppException_fromRestClient() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class))
                    .thenThrow(new AppException(ErrorCode.PAYMENT_FAILED, "Already captured"));

            assertThatThrownBy(() -> paypalClient.captureOrder("PAYPAL_ORDER_001"))
                    .isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }

        @Test
        void includesCaptureUriPath_inRequest() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class)).thenReturn(emptyCaptureResponse());

            paypalClient.captureOrder("ORDER-XYZ-789");

            verify(uriSpec).uri(argThatContains("/v2/checkout/orders/ORDER-XYZ-789/capture"));
        }

        @Test
        void sendsContentTypeJson_andAcceptJson_andEmptyBody() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class)).thenReturn(emptyCaptureResponse());

            paypalClient.captureOrder("PAYPAL_ORDER_001");

            verify(bodySpec).contentType(MediaType.APPLICATION_JSON);
            verify(bodySpec).header("Accept", "application/json");
            verify(bodySpec).body(Map.of());
        }

        @Test
        void throwsPaymentFailed_on415UnsupportedMediaType() {
            when(responseSpec.body(PaypalCaptureOrderResponse.class))
                    .thenThrow(HttpClientErrorException.create(
                            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                            "Unsupported Media Type",
                            null,
                            "{\"name\":\"UNSUPPORTED_MEDIA_TYPE\"}".getBytes(),
                            null));

            assertThatThrownBy(() -> paypalClient.captureOrder("PAYPAL_ORDER_001"))
                    .isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_FAILED);
        }
    }

    @Nested
    class VerifyWebhookSignature {

        private static final String VALID_RAW_BODY = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\","
                + "\"id\":\"WH-001\",\"resource\":{\"id\":\"CAP001\",\"status\":\"COMPLETED\"}}";

        @Test
        void returnsTrue_whenPaypalVerificationSucceeds() {
            when(responseSpec.body(PaypalWebhookVerifyResponse.class)).thenReturn(verifyResponse("SUCCESS"));

            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA",
                    "https://api.paypal.com/cert",
                    "TXN-001",
                    "sig-value",
                    "2025-01-01T00:00:00Z",
                    VALID_RAW_BODY);

            assertThat(result).isTrue();
        }

        @Test
        void returnsFalse_whenVerificationStatusIsNotSuccess() {
            when(responseSpec.body(PaypalWebhookVerifyResponse.class)).thenReturn(verifyResponse("FAILURE"));

            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA",
                    "https://api.paypal.com/cert",
                    "TXN-001",
                    "sig-value",
                    "2025-01-01T00:00:00Z",
                    VALID_RAW_BODY);

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalse_whenResponseBodyIsNull() {
            when(responseSpec.body(PaypalWebhookVerifyResponse.class)).thenReturn(null);

            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA",
                    "https://api.paypal.com/cert",
                    "TXN-001",
                    "sig-value",
                    "2025-01-01T00:00:00Z",
                    VALID_RAW_BODY);

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalse_onRestClientException() {
            when(responseSpec.body(PaypalWebhookVerifyResponse.class))
                    .thenThrow(new RestClientException("timeout"));

            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA",
                    "https://api.paypal.com/cert",
                    "TXN-001",
                    "sig-value",
                    "2025-01-01T00:00:00Z",
                    VALID_RAW_BODY);

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalse_whenRawBodyIsNotValidJson() {
            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA",
                    "https://api.paypal.com/cert",
                    "TXN-001",
                    "sig-value",
                    "2025-01-01T00:00:00Z",
                    "not-json-body");

            assertThat(result).isFalse();
            verify(responseSpec, never()).body(any(Class.class));
        }

        @Test
        void returnsFalse_whenWebhookIdNotConfigured() {
            when(configResolver.resolvePaypal()).thenReturn(new PaypalResolvedPaymentConfig(
                    true,
                    "SANDBOX",
                    "test-client-id",
                    "test-secret",
                    "https://api-m.sandbox.paypal.com",
                    "http://localhost:5173/payment/paypal/return",
                    "http://localhost:5173/payment/paypal/cancel",
                    "",
                    "USD",
                    "Locen Studio",
                    "en-US",
                    "PAY_NOW",
                    "IMMEDIATE_PAYMENT_REQUIRED",
                    "NO_SHIPPING",
                    new BigDecimal("25000"),
                    30_000,
                    30_000
            ));

            boolean result = paypalClient.verifyWebhookSignature(
                    "SHA256withRSA",
                    "https://api.paypal.com/cert",
                    "TXN-001",
                    "sig-value",
                    "2025-01-01T00:00:00Z",
                    VALID_RAW_BODY);

            assertThat(result).isFalse();
            verify(restClient, never()).post();
        }
    }

    private static String argThatContains(String substring) {
        return org.mockito.ArgumentMatchers.argThat(value -> value != null && value.contains(substring));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception exception) {
            throw new RuntimeException("Could not set field " + fieldName, exception);
        }
    }
}
