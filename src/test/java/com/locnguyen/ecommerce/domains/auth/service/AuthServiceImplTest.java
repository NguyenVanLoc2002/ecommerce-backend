package com.locnguyen.ecommerce.domains.auth.service;

import com.locnguyen.ecommerce.common.config.AppProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.security.AuthPrincipalType;
import com.locnguyen.ecommerce.common.security.JwtTokenProvider;
import com.locnguyen.ecommerce.common.security.RefreshSession;
import com.locnguyen.ecommerce.common.security.RefreshSessionService;
import com.locnguyen.ecommerce.common.security.TokenBlacklistService;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.auth.dto.TokenResponse;
import com.locnguyen.ecommerce.domains.auth.mapper.UserMapper;
import com.locnguyen.ecommerce.domains.auth.service.impl.AuthServiceImpl;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.customer.repository.CustomerRepository;
import com.locnguyen.ecommerce.domains.user.entity.Role;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.RoleRepository;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock JwtTokenProvider tokenProvider;
    @Mock TokenBlacklistService tokenBlacklistService;
    @Mock RefreshSessionService refreshSessionService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock CustomerRepository customerRepository;
    @Mock UserMapper userMapper;
    @Mock AuditLogService auditLogService;

    private AuthServiceImpl authService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getJwt().setAccessTokenExpiration(3_600_000L);
        appProperties.getJwt().setRefreshTokenExpiration(604_800_000L);

        authService = new AuthServiceImpl(
                authenticationManager,
                tokenProvider,
                tokenBlacklistService,
                refreshSessionService,
                passwordEncoder,
                userRepository,
                roleRepository,
                customerRepository,
                userMapper,
                appProperties,
                auditLogService
        );
    }

    @Test
    void refresh_rotates_session_when_token_and_redis_session_are_valid() {
        User user = user(USER_ID, UserStatus.ACTIVE, RoleName.STAFF);
        JwtTokenProvider.RefreshTokenClaims claims = new JwtTokenProvider.RefreshTokenClaims(
                user.getEmail(),
                AuthPrincipalType.USER,
                USER_ID,
                "old-session",
                "family-1",
                new Date(),
                new Date(System.currentTimeMillis() + 60_000L)
        );
        RefreshSession storedSession = RefreshSession.builder()
                .sessionId("old-session")
                .familyId("family-1")
                .principalType(AuthPrincipalType.USER)
                .principalId(USER_ID)
                .tokenHash("old-hash")
                .build();

        when(tokenProvider.validateToken("old-refresh")).thenReturn(true);
        when(tokenProvider.isRefreshToken("old-refresh")).thenReturn(true);
        when(tokenProvider.extractRefreshTokenClaims("old-refresh")).thenReturn(claims);
        when(refreshSessionService.getSession(AuthPrincipalType.USER, USER_ID, "old-session"))
                .thenReturn(storedSession);
        when(refreshSessionService.hashToken("old-refresh")).thenReturn("old-hash");
        when(userRepository.findByIdAndDeletedFalse(USER_ID)).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken("user@example.com", List.of("STAFF"))).thenReturn("new-access");
        when(tokenProvider.generateRefreshToken(
                eq("user@example.com"),
                eq(AuthPrincipalType.USER),
                eq(USER_ID),
                anyString(),
                eq("family-1")
        )).thenReturn("new-refresh");
        when(refreshSessionService.hashToken("new-refresh")).thenReturn("new-hash");

        AuthService.TokenRefreshResponse response = authService.refreshToken(
                "old-refresh",
                null,
                new AuthService.ClientMetadata("JUnit", "127.0.0.1")
        );

        assertThat(response.response().getAccessToken()).isEqualTo("new-access");
        assertThat(response.response().getExpiresIn()).isEqualTo(3600L);
        assertThat(response.refreshToken()).isEqualTo("new-refresh");

        verify(refreshSessionService).revokeSession(AuthPrincipalType.USER, USER_ID, "old-session");

        ArgumentCaptor<RefreshSession> sessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
        verify(refreshSessionService).storeSession(sessionCaptor.capture(), any());
        RefreshSession newSession = sessionCaptor.getValue();
        assertThat(newSession.getFamilyId()).isEqualTo("family-1");
        assertThat(newSession.getPrincipalType()).isEqualTo(AuthPrincipalType.USER);
        assertThat(newSession.getPrincipalId()).isEqualTo(USER_ID);
        assertThat(newSession.getTokenHash()).isEqualTo("new-hash");
        assertThat(newSession.getUserAgent()).isEqualTo("JUnit");
        assertThat(newSession.getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void refresh_rejects_reused_token_and_revokes_family_when_session_is_missing() {
        JwtTokenProvider.RefreshTokenClaims claims = new JwtTokenProvider.RefreshTokenClaims(
                "user@example.com",
                AuthPrincipalType.USER,
                USER_ID,
                "missing-session",
                "family-1",
                new Date(),
                new Date(System.currentTimeMillis() + 60_000L)
        );

        when(tokenProvider.validateToken("reused-refresh")).thenReturn(true);
        when(tokenProvider.isRefreshToken("reused-refresh")).thenReturn(true);
        when(tokenProvider.extractRefreshTokenClaims("reused-refresh")).thenReturn(claims);
        when(refreshSessionService.getSession(AuthPrincipalType.USER, USER_ID, "missing-session"))
                .thenReturn(null);

        assertThatThrownBy(() -> authService.refreshToken(
                "reused-refresh",
                null,
                new AuthService.ClientMetadata("JUnit", "127.0.0.1")
        ))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);

        verify(refreshSessionService).revokeFamily(AuthPrincipalType.USER, USER_ID, "family-1");
    }

    @Test
    void refresh_rejects_hash_mismatch_and_revokes_family() {
        JwtTokenProvider.RefreshTokenClaims claims = new JwtTokenProvider.RefreshTokenClaims(
                "user@example.com",
                AuthPrincipalType.USER,
                USER_ID,
                "old-session",
                "family-1",
                new Date(),
                new Date(System.currentTimeMillis() + 60_000L)
        );
        RefreshSession storedSession = RefreshSession.builder()
                .sessionId("old-session")
                .familyId("family-1")
                .principalType(AuthPrincipalType.USER)
                .principalId(USER_ID)
                .tokenHash("expected-hash")
                .build();

        when(tokenProvider.validateToken("tampered-refresh")).thenReturn(true);
        when(tokenProvider.isRefreshToken("tampered-refresh")).thenReturn(true);
        when(tokenProvider.extractRefreshTokenClaims("tampered-refresh")).thenReturn(claims);
        when(refreshSessionService.getSession(AuthPrincipalType.USER, USER_ID, "old-session"))
                .thenReturn(storedSession);
        when(refreshSessionService.hashToken("tampered-refresh")).thenReturn("different-hash");

        assertThatThrownBy(() -> authService.refreshToken(
                "tampered-refresh",
                null,
                new AuthService.ClientMetadata("JUnit", "127.0.0.1")
        ))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);

        verify(refreshSessionService).revokeFamily(AuthPrincipalType.USER, USER_ID, "family-1");
    }

    @Test
    void logout_blacklists_access_token_and_revokes_refresh_session() {
        JwtTokenProvider.RefreshTokenClaims claims = new JwtTokenProvider.RefreshTokenClaims(
                "user@example.com",
                AuthPrincipalType.USER,
                USER_ID,
                "session-1",
                "family-1",
                new Date(),
                new Date(System.currentTimeMillis() + 60_000L)
        );

        when(tokenProvider.validateToken("access-token")).thenReturn(true);
        when(tokenProvider.isAccessToken("access-token")).thenReturn(true);
        when(tokenProvider.extractUsername("access-token")).thenReturn("user@example.com");
        when(tokenProvider.extractRefreshTokenClaimsAllowExpired("refresh-token")).thenReturn(claims);

        authService.logout("access-token", "refresh-token");

        verify(tokenBlacklistService).blacklist("access-token");
        verify(refreshSessionService).revokeSession(AuthPrincipalType.USER, USER_ID, "session-1");
    }

    @Test
    void logout_is_idempotent_when_tokens_are_missing_or_invalid() {
        when(tokenProvider.extractRefreshTokenClaimsAllowExpired("bad-refresh")).thenReturn(null);

        authService.logout(null, "bad-refresh");

        verify(tokenBlacklistService, never()).blacklist(anyString());
        verify(refreshSessionService, never()).revokeSession(any(), any(), anyString());
    }

    @Test
    void refresh_fails_after_logout_revokes_the_session() {
        JwtTokenProvider.RefreshTokenClaims claims = new JwtTokenProvider.RefreshTokenClaims(
                "user@example.com",
                AuthPrincipalType.USER,
                USER_ID,
                "session-1",
                "family-1",
                new Date(),
                new Date(System.currentTimeMillis() + 60_000L)
        );

        when(tokenProvider.extractRefreshTokenClaimsAllowExpired("refresh-token")).thenReturn(claims);
        authService.logout(null, "refresh-token");

        verify(refreshSessionService).revokeSession(AuthPrincipalType.USER, USER_ID, "session-1");

        when(tokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(tokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(tokenProvider.extractRefreshTokenClaims("refresh-token")).thenReturn(claims);
        when(refreshSessionService.getSession(AuthPrincipalType.USER, USER_ID, "session-1"))
                .thenReturn(null);

        assertThatThrownBy(() -> authService.refreshToken(
                "refresh-token",
                null,
                new AuthService.ClientMetadata("JUnit", "127.0.0.1")
        ))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);

        verify(refreshSessionService).revokeFamily(AuthPrincipalType.USER, USER_ID, "family-1");
    }

    @Test
    void revoke_all_refresh_sessions_delegates_to_refresh_session_service() {
        authService.revokeAllRefreshSessions(AuthPrincipalType.CUSTOMER, CUSTOMER_ID);

        verify(refreshSessionService).revokeAllRefreshSessions(AuthPrincipalType.CUSTOMER, CUSTOMER_ID);
    }

    private User user(UUID id, UserStatus status, RoleName... roles) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user@example.com");
        user.setStatus(status);
        for (RoleName roleName : roles) {
            Role role = new Role();
            role.setName(roleName);
            user.getRoles().add(role);
        }
        return user;
    }
}
