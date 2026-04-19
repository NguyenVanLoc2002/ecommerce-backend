package com.locnguyen.ecommerce.domains.auth.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.auth.dto.*;
import com.locnguyen.ecommerce.domains.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Register, login, and token management")
@RestController
@RequestMapping(AppConstants.API_V1 + "/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Register a new customer account",
            description = "Creates a customer account with email/password. " +
                    "Returns user profile and JWT tokens (auto-login)."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "Account created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Email or phone already registered",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ApiResponse.created(response);
    }

    @Operation(
            summary = "Login with email and password",
            description = "Authenticates a user and returns JWT tokens. " +
                    "Access token is short-lived; use refresh token to obtain new tokens."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Login successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Invalid credentials or account disabled",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "Refresh access token",
            description = "Exchanges a valid refresh token for a new access + refresh token pair. " +
                    "The old refresh token is not explicitly revoked in this implementation."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Tokens refreshed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Refresh token is invalid or expired",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @PostMapping("/refresh-token")
    public ApiResponse<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.refreshToken(request);
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "Logout",
            description = "Invalidates the current access token. The token is added to a Redis blacklist " +
                    "and rejected on all subsequent requests until its natural expiry.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Logged out successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Token is missing or invalid",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = extractBearerToken(request);
        authService.logout(token);
        return ApiResponse.noContent();
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AppConstants.AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(AppConstants.BEARER_PREFIX)) {
            return header.substring(AppConstants.BEARER_PREFIX.length());
        }
        throw new AppException(ErrorCode.TOKEN_INVALID, "Authorization header is missing or malformed");
    }
}
