package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Component
public class AhamoveClient {

    private final RestClient restClient;

    public AhamoveClient(@Qualifier("ahamoveRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<AhamoveEstimateOptionResponse> estimateFee(
            AhamoveEstimateRequest request,
            AhamoveResolvedConfig config) {
        return execute("estimateFee", true, () -> {
            String token = authenticate(config);
            AhamoveEstimateOptionResponse[] response = restClient.post()
                    .uri(config.apiBaseUrl() + "/v3/orders/estimates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(request)
                    .retrieve()
                    .body(AhamoveEstimateOptionResponse[].class);
            if (response == null) {
                throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                        "AhaMove estimate fee returned empty response");
            }
            return Arrays.asList(response);
        });
    }

    public AhamoveCreateOrderResponse createOrder(
            AhamoveCreateOrderRequest request,
            AhamoveResolvedConfig config) {
        return execute("createOrder", false, () -> {
            String token = authenticate(config);
            AhamoveCreateOrderResponse response = restClient.post()
                    .uri(config.apiBaseUrl() + "/v3/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(request)
                    .retrieve()
                    .body(AhamoveCreateOrderResponse.class);
            if (response == null || isBlank(response.getOrderId())) {
                throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                        "AhaMove create order returned empty response");
            }
            return response;
        });
    }

    public boolean cancelOrder(String providerOrderId, String trackingNumber, String reason,
                               AhamoveResolvedConfig config) {
        if (isBlank(providerOrderId) && isBlank(trackingNumber)) {
            throw new AppException(ErrorCode.SHIPMENT_PROVIDER_ORDER_NOT_FOUND,
                    "AhaMove shipment has no provider order id or tracking number");
        }
        return execute("cancelOrder", false, () -> {
            String token = authenticate(config);
            if (!isBlank(providerOrderId)) {
                restClient.method(HttpMethod.DELETE)
                        .uri(config.apiBaseUrl() + "/v3/orders/{orderId}", providerOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(new AhamoveCancelOrderRequest(reason))
                        .retrieve()
                        .toBodilessEntity();
                return true;
            }
            restClient.method(HttpMethod.DELETE)
                    .uri(config.apiBaseUrl() + "/v3/orders/tracks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(new AhamoveCancelOrderByTrackingRequest(trackingNumber, reason))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        });
    }

    public AhamoveOrderPayload getOrderInfo(String providerOrderId, AhamoveResolvedConfig config) {
        if (isBlank(providerOrderId)) {
            throw new AppException(ErrorCode.SHIPMENT_PROVIDER_ORDER_NOT_FOUND,
                    "AhaMove provider order id is missing");
        }
        return execute("getOrderInfo", true, () -> {
            String token = authenticate(config);
            AhamoveOrderPayload response = restClient.get()
                    .uri(config.apiBaseUrl() + "/v3/orders/{orderId}", providerOrderId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(AhamoveOrderPayload.class);
            if (response == null || isBlank(response.getId())) {
                throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                        "AhaMove order detail returned empty response");
            }
            return response;
        });
    }

    public String getTrackingLink(String providerOrderId, AhamoveResolvedConfig config) {
        if (isBlank(providerOrderId)) {
            return null;
        }
        return execute("getTrackingLink", true, () -> {
            String token = authenticate(config);
            AhamoveTrackLinkResponse response = restClient.get()
                    .uri(config.apiBaseUrl() + "/v3/orders/{orderId}/shared-link", providerOrderId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(AhamoveTrackLinkResponse.class);
            return response != null ? response.getSharedLink() : null;
        });
    }

    public void verifyConnection(AhamoveResolvedConfig config) {
        authenticate(config);
    }

    private String authenticate(AhamoveResolvedConfig config) {
        return execute("authenticate", true, () -> {
            AhamoveAuthResponse response = restClient.post()
                    .uri(config.apiBaseUrl() + "/v3/accounts/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(AhamoveAuthRequest.builder()
                            .mobile(config.phone())
                            .apiKey(config.apiKey())
                            .build())
                    .retrieve()
                    .body(AhamoveAuthResponse.class);
            if (response == null || isBlank(response.getToken())) {
                throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                        "AhaMove token response is empty");
            }
            return response.getToken();
        });
    }

    private <T> T execute(String action, boolean safeToRetry, Supplier<T> supplier) {
        int maxAttempts = safeToRetry ? 2 : 1;
        AppException lastAppException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (AppException ex) {
                lastAppException = ex;
                throw ex;
            } catch (RestClientResponseException ex) {
                if (safeToRetry && ex.getStatusCode().is5xxServerError() && attempt < maxAttempts) {
                    log.warn("Retrying AhaMove action after server error: action={} status={} attempt={}/{}",
                            action, ex.getStatusCode(), attempt, maxAttempts);
                    continue;
                }
                throw toCarrierException(action, ex);
            } catch (ResourceAccessException ex) {
                if (safeToRetry && attempt < maxAttempts) {
                    log.warn("Retrying AhaMove action after transport timeout/error: action={} attempt={}/{} error={}",
                            action, attempt, maxAttempts, ex.getMessage());
                    continue;
                }
                throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                        "AhaMove " + action + " failed: " + sanitize(ex.getMessage()));
            } catch (RestClientException ex) {
                throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                        "AhaMove " + action + " failed: " + sanitize(ex.getMessage()));
            }
        }
        throw lastAppException != null
                ? lastAppException
                : new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                "AhaMove " + action + " failed");
    }

    private AppException toCarrierException(String action, RestClientResponseException ex) {
        String body = sanitize(ex.getResponseBodyAsString());
        if (ex.getStatusCode().is4xxClientError()) {
            return new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                    "AhaMove " + action + " rejected the request [" + ex.getRawStatusCode() + "]: " + body);
        }
        return new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                "AhaMove " + action + " failed [" + ex.getRawStatusCode() + "]: " + body);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("(?i)(api[_-]?key|authorization|token)\\s*[:=]\\s*[^,}\\s]+", "$1=***");
        if (normalized.length() > 300) {
            return normalized.substring(0, 300);
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record AhamoveCancelOrderRequest(String comment) {}

    private record AhamoveCancelOrderByTrackingRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("tracking_number")
            String trackingNumber,
            String comment
    ) {}
}
