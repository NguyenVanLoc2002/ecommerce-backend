package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AhamoveAuthResponse {

    private String token;

    @JsonProperty("refresh_token")
    private String refreshToken;
}
