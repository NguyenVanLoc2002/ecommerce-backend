package com.locnguyen.ecommerce.domains.carrier.mapper;

import com.locnguyen.ecommerce.domains.carrier.dto.CarrierResponse;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CarrierMapper {

    default CarrierResponse toResponse(Carrier carrier) {
        if (carrier == null) {
            return null;
        }
        return toResponse(carrier, carrier.getConfig());
    }

    default CarrierResponse toResponse(Carrier carrier, CarrierConfig config) {
        if (carrier == null) {
            return null;
        }
        return CarrierResponse.builder()
                .id(carrier.getId())
                .code(carrier.getCode())
                .name(carrier.getName())
                .providerType(carrier.getProviderType())
                .status(carrier.getStatus())
                .logoUrl(carrier.getLogoUrl())
                .description(carrier.getDescription())
                .configEnabled(config != null ? config.isEnabled() : null)
                .baseUrl(config != null ? config.getBaseUrl() : null)
                .hasApiKey(config != null && config.getApiKeyEnc() != null && !config.getApiKeyEnc().isBlank())
                .hasSecretKey(config != null && config.getSecretKeyEnc() != null && !config.getSecretKeyEnc().isBlank())
                .hasWebhookSecret(config != null && config.getWebhookSecretEnc() != null
                        && !config.getWebhookSecretEnc().isBlank())
                .configJson(config != null ? config.getConfigJson() : null)
                .connectionStatus(config != null ? config.getConnectionStatus() : null)
                .lastHealthCheckAt(config != null ? config.getLastHealthCheckAt() : null)
                .lastHealthCheckError(config != null ? config.getLastHealthCheckError() : null)
                .createdAt(carrier.getCreatedAt())
                .updatedAt(carrier.getUpdatedAt())
                .build();
    }
}
