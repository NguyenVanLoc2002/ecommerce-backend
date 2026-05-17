package com.locnguyen.ecommerce.domains.carrier.dto;

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

    @Size(max = 5000)
    private String configJson;
}
