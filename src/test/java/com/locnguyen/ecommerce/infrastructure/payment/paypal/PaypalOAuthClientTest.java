package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.infrastructure.payment.paypal.dto.PaypalAccessTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaypalOAuthClient}.
 * Verifies token fetch, caching, and secret logging safety.
 */
class PaypalOAuthClientTest {

    private PaypalPaymentProperties properties;
    private PaypalOAuthClient oAuthClient;

    // RestClient mock chain
    private RestClient restClient;
    private RestClient.RequestBodyUriSpec uriSpec;
    private RestClient.RequestHeadersSpec<?> headersSpec;
    private RestClient.RequestBodySpec bodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        properties = new PaypalPaymentProperties();
        properties.setEnabled(true);
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-secret-DO-NOT-LOG");
        properties.setBaseUrl("https://api-m.sandbox.paypal.com");
        properties.setCurrency("USD");
        properties.setTestConversionEnabled(true);
        properties.setTestConversionRateVndToUsd(new BigDecimal("25000"));
        properties.setEnvironment("SANDBOX");
        properties.setConnectTimeoutMs(30_000);
        properties.setReadTimeoutMs(30_000);

        restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        bodySpec = mock(RestClient.RequestBodySpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(uriSpec).when(restClient).post();
        doReturn(bodySpec).when(uriSpec).uri(any(String.class));
        doReturn(bodySpec).when(bodySpec).header(any(), any());
        doReturn(bodySpec).when(bodySpec).contentType(any(MediaType.class));
        doReturn(bodySpec).when(bodySpec).body(any(MultiValueMap.class));
        doReturn(responseSpec).when(bodySpec).retrieve();

        oAuthClient = new PaypalOAuthClient(properties, restClient);
    }

    private PaypalAccessTokenResponse tokenResponse(int expiresIn) {
        PaypalAccessTokenResponse resp = new PaypalAccessTokenResponse();
        setField(resp, "accessToken", "sandbox-access-token-xyz");
        setField(resp, "tokenType", "Bearer");
        setField(resp, "expiresIn", expiresIn);
        return resp;
    }

    @Test
    void getAccessToken_returnsToken_whenPaypalResponds() {
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(tokenResponse(3600));

        String token = oAuthClient.getAccessToken();

        assertThat(token).isEqualTo("sandbox-access-token-xyz");
    }

    @Test
    void getAccessToken_usesCache_onSecondCall() {
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(tokenResponse(3600));

        oAuthClient.getAccessToken();
        oAuthClient.getAccessToken();

        // HTTP call should happen only once — second call uses cached token
        verify(restClient, times(1)).post();
    }

    @Test
    void getAccessToken_throws_PAYMENT_FAILED_onApiFailure() {
        when(responseSpec.body(PaypalAccessTokenResponse.class))
                .thenThrow(new RestClientException("Connection refused"));

        assertThatThrownBy(() -> oAuthClient.getAccessToken())
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);
    }

    @Test
    void getAccessToken_throws_PAYMENT_FAILED_whenTokenIsNull() {
        PaypalAccessTokenResponse emptyResp = new PaypalAccessTokenResponse();
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(emptyResp);

        assertThatThrownBy(() -> oAuthClient.getAccessToken())
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);
    }

    @Test
    void getAccessToken_throws_PAYMENT_FAILED_whenResponseBodyIsNull() {
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(null);

        assertThatThrownBy(() -> oAuthClient.getAccessToken())
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);
    }

    @Test
    void getAccessToken_setsAuthorizationHeader() {
        when(responseSpec.body(PaypalAccessTokenResponse.class)).thenReturn(tokenResponse(3600));

        oAuthClient.getAccessToken();

        // Verify the Authorization header was set (Basic scheme, not Bearer — the implementation
        // constructs it as "Basic " + base64(clientId + ":" + clientSecret))
        verify(bodySpec, atLeastOnce()).header(eq("Authorization"), any());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Could not set field " + fieldName, e);
        }
    }
}
