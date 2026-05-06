package com.locnguyen.ecommerce.domains.auth.service;

import com.locnguyen.ecommerce.common.security.AuthPrincipalType;
import com.locnguyen.ecommerce.domains.auth.dto.AuthResponse;
import com.locnguyen.ecommerce.domains.auth.dto.LoginRequest;
import com.locnguyen.ecommerce.domains.auth.dto.RegisterRequest;
import com.locnguyen.ecommerce.domains.auth.dto.TokenResponse;

import java.util.UUID;

public interface AuthService {

    void logout(String accessToken, String refreshToken);

    AuthenticatedSessionResponse register(RegisterRequest request, ClientMetadata clientMetadata);

    AuthenticatedSessionResponse login(LoginRequest request, ClientMetadata clientMetadata);

    TokenRefreshResponse refreshToken(String refreshTokenFromCookie,
                                      String refreshTokenFromBody,
                                      ClientMetadata clientMetadata);

    void revokeAllRefreshSessions(AuthPrincipalType principalType, UUID principalId);

    record ClientMetadata(String userAgent, String ipAddress) {
    }

    record AuthenticatedSessionResponse(AuthResponse response, String refreshToken) {
    }

    record TokenRefreshResponse(TokenResponse response, String refreshToken) {
    }
}
