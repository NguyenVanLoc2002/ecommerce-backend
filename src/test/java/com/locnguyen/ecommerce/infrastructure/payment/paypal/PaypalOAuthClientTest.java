package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.payment.config.PaymentProviderConfigResolver;
import com.locnguyen.ecommerce.domains.payment.config.PaypalResolvedPaymentConfig;
import com.locnguyen.ecommerce.infrastructure.payment.PaymentRestClientFactory;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalAccessTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaypalOAuthClientTest {

    @Mock
    private PaymentProviderConfigResolver configResolver;
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

    private PaypalResolvedPaymentConfig paypalConfig;
    private PaypalOAuthClient oAuthClient;

    @BeforeEach
    void setUp() {
        paypalConfig = new PaypalResolvedPaymentConfig(
                true,
                "SANDBOX",
                "test-client-id",
                "test-secret-DO-NOT-LOG",
                "https://api-m.sandbox.paypal.com",
                "http://localhost:5173/payment/paypal/return",
                "http://localhost:5173/payment/paypal/cancel",
                "WH-123456",
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
        when(restClientFactory.create(anyInt(), anyInt())).thenReturn(restClient);

        doReturn(uriSpec).when(restClient).post();
        doReturn(bodySpec).when(uriSpec).uri(any(String.class));
        doReturn(bodySpec).when(bodySpec).header(any(), any());
        doReturn(bodySpec).when(bodySpec).contentType(any(MediaType.class));
        doReturn(bodySpec).when(bodySpec).body(any(MultiValueMap.class));
        doReturn(responseSpec).when(bodySpec).retrieve();

        oAuthClient = new PaypalOAuthClient(configResolver, restClientFactory);
    }

    private PaypalAccessTokenResponse tokenResponse(int expiresIn) {
        PaypalAccessTokenResponse response = new PaypalAccessTokenResponse();
        setField(response, "accessToken", "sandbox-access-token-xyz");
        setField(response, "tokenType", "Bearer");
        setField(response, "expiresIn", expiresIn);
        return response;
    }

    @Test
    void getAccessToken_returnsToken_whenPaypalResponds() {
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(tokenResponse(3600));

        String token = oAuthClient.getAccessToken();

        assertThat(token).isEqualTo("sandbox-access-token-xyz");
        verify(restClientFactory).create(30_000, 30_000);
    }

    @Test
    void getAccessToken_usesCache_onSecondCall() {
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(tokenResponse(3600));

        oAuthClient.getAccessToken();
        oAuthClient.getAccessToken();

        verify(restClient, times(1)).post();
    }

    @Test
    void getAccessToken_refreshes_whenConfigFingerprintChanges() {
        when(configResolver.resolvePaypal()).thenReturn(
                paypalConfig,
                new PaypalResolvedPaymentConfig(
                        true,
                        "SANDBOX",
                        "other-client-id",
                        "test-secret-DO-NOT-LOG",
                        "https://api-m.sandbox.paypal.com",
                        "http://localhost:5173/payment/paypal/return",
                        "http://localhost:5173/payment/paypal/cancel",
                        "WH-123456",
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
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(tokenResponse(3600));

        oAuthClient.getAccessToken();
        oAuthClient.getAccessToken();

        verify(restClient, times(2)).post();
    }

    @Test
    void getAccessToken_throwsPaymentFailed_onApiFailure() {
        when(responseSpec.body(PaypalAccessTokenResponse.class))
                .thenThrow(new RestClientException("Connection refused"));

        assertThatThrownBy(() -> oAuthClient.getAccessToken())
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);
    }

    @Test
    void getAccessToken_throwsPaymentFailed_whenTokenMissing() {
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(new PaypalAccessTokenResponse());

        assertThatThrownBy(() -> oAuthClient.getAccessToken())
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);
    }

    @Test
    void getAccessToken_throwsPaymentFailed_whenResponseBodyIsNull() {
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(null);

        assertThatThrownBy(() -> oAuthClient.getAccessToken())
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);
    }

    @Test
    void getAccessToken_setsAuthorizationHeader() {
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(tokenResponse(3600));

        oAuthClient.getAccessToken();

        verify(bodySpec).header(eq("Authorization"), any());
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
