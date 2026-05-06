package com.locnguyen.ecommerce.domains.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * Combined response for register and login.
 * Returns user info and access-token metadata while the refresh token is managed via HttpOnly cookie.
 */
@Getter
@Builder
@Schema(description = "Authentication response containing user profile and access-token metadata")
public class AuthResponse {

    @Schema(description = "Authenticated user profile")
    private final UserResponse user;

    @Schema(description = "Access-token metadata")
    private final TokenResponse tokens;
}
