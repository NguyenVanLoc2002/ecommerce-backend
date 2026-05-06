package com.locnguyen.ecommerce.domains.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Deprecated refresh token request body. Prefer the HttpOnly refresh-token cookie.")
public class RefreshTokenRequest {

    @Schema(
            example = "eyJhbGciOiJIUzI1NiJ9...",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            deprecated = true
    )
    private String refreshToken;
}
