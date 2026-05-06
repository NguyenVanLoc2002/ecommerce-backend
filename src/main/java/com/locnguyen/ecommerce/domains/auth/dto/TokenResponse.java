package com.locnguyen.ecommerce.domains.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Access-token response payload")
public class TokenResponse {

    @Schema(description = "JWT access token used in the Authorization header", example = "eyJhbGci...")
    private final String accessToken;

    @Schema(description = "Token type", example = "Bearer")
    private final String tokenType;

    @Schema(description = "Access token expiration in seconds", example = "3600")
    private final long expiresIn;
}
