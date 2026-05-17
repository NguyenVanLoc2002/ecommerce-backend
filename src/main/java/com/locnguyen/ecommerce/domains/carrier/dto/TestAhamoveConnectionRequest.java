package com.locnguyen.ecommerce.domains.carrier.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestAhamoveConnectionRequest {

    @Size(max = 500)
    private String apiKey;

    @Size(max = 500)
    private String baseUrl;

    @Size(max = 50)
    private String phone;
}
