package com.locnguyen.ecommerce.domains.auth.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.security.RefreshTokenCookieService;
import com.locnguyen.ecommerce.domains.auth.dto.AuthResponse;
import com.locnguyen.ecommerce.domains.auth.dto.LoginRequest;
import com.locnguyen.ecommerce.domains.auth.dto.RefreshTokenRequest;
import com.locnguyen.ecommerce.domains.auth.dto.RegisterRequest;
import com.locnguyen.ecommerce.domains.auth.dto.TokenResponse;
import com.locnguyen.ecommerce.domains.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@Tag(name = "Authentication", description = "Register, login, refresh, and logout")
@RestController
@RequestMapping(AppConstants.API_V1 + "/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieService refreshTokenCookieService;

    @Operation(
            summary = "Register a new customer account",
            description = "Creates a customer account, returns user profile plus access-token metadata, " +
                    "and sets the refresh token in an HttpOnly cookie."
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
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        AuthService.AuthenticatedSessionResponse authResult =
                authService.register(request, buildClientMetadata(httpRequest));
        refreshTokenCookieService.addRefreshTokenCookie(httpResponse, authResult.refreshToken());
        return ApiResponse.created(authResult.response());
    }

    @Operation(
            summary = "Login with email and password",
            description = "Authenticates a user, returns access-token metadata in the response body, " +
                    "and sets the refresh token in an HttpOnly cookie."
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
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        AuthService.AuthenticatedSessionResponse authResult =
                authService.login(request, buildClientMetadata(httpRequest));
        refreshTokenCookieService.addRefreshTokenCookie(httpResponse, authResult.refreshToken());
        return ApiResponse.success(authResult.response());
    }

    @Operation(
            summary = "Refresh access token",
            description = "Rotates the HttpOnly refresh-token cookie and returns a new access token. " +
                    "Request-body refresh tokens are supported temporarily for backward compatibility."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Tokens refreshed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Refresh token is invalid or expired",
                    content = @Content(schema = @Schema(implementation = com.locnguyen.ecommerce.common.response.ErrorResponse.class)))
    })
    @PostMapping("/refresh-token")
    public ApiResponse<TokenResponse> refreshToken(@RequestBody(required = false) RefreshTokenRequest request,
                                                   HttpServletRequest httpRequest,
                                                   HttpServletResponse httpResponse) {
        String cookieRefreshToken = refreshTokenCookieService.extractRefreshToken(httpRequest);
        String bodyRefreshToken = request != null ? request.getRefreshToken() : null;

        try {
            AuthService.TokenRefreshResponse refreshResult = authService.refreshToken(
                    cookieRefreshToken,
                    bodyRefreshToken,
                    buildClientMetadata(httpRequest)
            );
            refreshTokenCookieService.addRefreshTokenCookie(httpResponse, refreshResult.refreshToken());
            return ApiResponse.success(refreshResult.response());
        } catch (RuntimeException ex) {
            refreshTokenCookieService.clearRefreshTokenCookie(httpResponse);
            throw ex;
        }
    }

    @Operation(
            summary = "Logout",
            description = "Clears the refresh-token cookie, revokes the refresh session if present, " +
                    "and blacklists the current access token when a valid Bearer token is supplied."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Logged out successfully")
    })
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(
                extractBearerToken(request),
                refreshTokenCookieService.extractRefreshToken(request)
        );
        refreshTokenCookieService.clearRefreshTokenCookie(response);
        return ApiResponse.noContent();
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AppConstants.AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(AppConstants.BEARER_PREFIX)) {
            return header.substring(AppConstants.BEARER_PREFIX.length());
        }
        return null;
    }

    private AuthService.ClientMetadata buildClientMetadata(HttpServletRequest request) {
        return new AuthService.ClientMetadata(
                request.getHeader("User-Agent"),
                resolveClientIp(request)
        );
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
        }
        return request.getRemoteAddr();
    }
}
