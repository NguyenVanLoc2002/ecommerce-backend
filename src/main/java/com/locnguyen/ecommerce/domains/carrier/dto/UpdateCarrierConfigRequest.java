package com.locnguyen.ecommerce.domains.carrier.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCarrierConfigRequest {

    @Size(max = 500)
    private String apiKey;

    @Size(max = 500)
    private String secretKey;

    @Size(max = 500)
    private String webhookSecret;

    @Size(max = 500)
    private String baseUrl;

    private Boolean enabled;

    @JsonSetter("configJson")
    public void rejectLegacyConfigJson(String ignored) {
        throw new IllegalArgumentException("configJson is no longer supported; use typed provider configuration");
    }
}
