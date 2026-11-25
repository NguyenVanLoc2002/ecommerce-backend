package com.locnguyen.ecommerce.domains.auth.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.security.RefreshTokenCookieService;
import com.locnguyen.ecommerce.domains.auth.dto.CustomerSessionResponse;
import com.locnguyen.ecommerce.domains.auth.service.CustomerSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(AppConstants.API_V1 + "/auth/sessions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearerAuth")
public class CustomerSessionController {

    private final CustomerSessionService customerSessionService;
    private final RefreshTokenCookieService refreshTokenCookieService;

    @Operation(summary = "List active customer sessions", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerSessionResponse>>> listSessions(HttpServletRequest request) {
        List<CustomerSessionResponse> sessions = customerSessionService
                .listCurrentCustomerSessions(refreshTokenCookieService.extractRefreshToken(request));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(sessions));
    }

    @Operation(summary = "Immediately revoke one other customer session")
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> revokeSession(@PathVariable UUID sessionId, HttpServletRequest request) {
        customerSessionService.revokeSession(sessionId, refreshTokenCookieService.extractRefreshToken(request));
        return ApiResponse.noContent();
    }

    @Operation(summary = "Immediately revoke all other customer sessions")
    @DeleteMapping
    public ApiResponse<Void> revokeOtherSessions(HttpServletRequest request) {
        customerSessionService.revokeOtherSessions(refreshTokenCookieService.extractRefreshToken(request));
        return ApiResponse.noContent();
    }
}
