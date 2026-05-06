package com.locnguyen.ecommerce.domains.auth.service.impl;

import com.locnguyen.ecommerce.common.config.AppProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.security.AuthPrincipalType;
import com.locnguyen.ecommerce.common.security.JwtTokenProvider;
import com.locnguyen.ecommerce.common.security.RefreshSession;
import com.locnguyen.ecommerce.common.security.RefreshSessionService;
import com.locnguyen.ecommerce.common.security.TokenBlacklistService;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.auth.dto.AuthResponse;
import com.locnguyen.ecommerce.domains.auth.dto.LoginRequest;
import com.locnguyen.ecommerce.domains.auth.dto.RegisterRequest;
import com.locnguyen.ecommerce.domains.auth.dto.TokenResponse;
import com.locnguyen.ecommerce.domains.auth.mapper.UserMapper;
import com.locnguyen.ecommerce.domains.auth.service.AuthService;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.customer.repository.CustomerRepository;
import com.locnguyen.ecommerce.domains.user.entity.Role;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.RoleRepository;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final RefreshSessionService refreshSessionService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CustomerRepository customerRepository;
    private final UserMapper userMapper;
    private final AppProperties appProperties;
    private final AuditLogService auditLogService;

    @Override
    public void logout(String accessToken, String refreshToken) {
        boolean accessTokenBlacklisted = false;

        if (tokenProvider.validateToken(accessToken) && tokenProvider.isAccessToken(accessToken)) {
            tokenBlacklistService.blacklist(accessToken);
            accessTokenBlacklisted = true;
        }

        revokeRefreshTokenIfPossible(refreshToken);

        if (accessTokenBlacklisted) {
            String email = tokenProvider.extractUsername(accessToken);
            log.info("User logged out: email={}", email);
            auditLogService.log(AuditAction.LOGOUT, "USER", email, "access_token_blacklisted");
        }
    }

    @Override
    @Transactional
    public AuthenticatedSessionResponse register(RegisterRequest request, ClientMetadata clientMetadata) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (request.getPhoneNumber() != null
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException(
                        "CUSTOMER role not found - check Flyway V2 seed data"));

        User user = new User();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName() != null ? request.getLastName().trim() : null);
        user.setPhoneNumber(request.getPhoneNumber());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(customerRole));

        user = userRepository.save(user);

        Customer customer = customerRepository.save(new Customer(user));

        log.info("User registered: id={} email={} customerId={}", user.getId(), user.getEmail(), customer.getId());
        auditLogService.log(AuditAction.USER_REGISTERED, "USER",
                String.valueOf(user.getId()), "email=" + user.getEmail());

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                AuthPrincipalType.CUSTOMER,
                customer.getId(),
                user
        );
        SessionTokenBundle tokens = issueSession(principal, clientMetadata, null);
        AuthResponse response = AuthResponse.builder()
                .user(userMapper.toUserResponse(user))
                .tokens(tokens.response())
                .build();
        return new AuthenticatedSessionResponse(response, tokens.refreshToken());
    }

    @Override
    @Transactional
    public AuthenticatedSessionResponse login(LoginRequest request, ClientMetadata clientMetadata) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase().trim(),
                            request.getPassword()
                    )
            );
        } catch (DisabledException e) {
            auditLogService.logWithActor(AuditAction.LOGIN_FAILURE, "USER",
                    request.getEmail(), request.getEmail(), "reason=ACCOUNT_DISABLED");
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        } catch (LockedException e) {
            auditLogService.logWithActor(AuditAction.LOGIN_FAILURE, "USER",
                    request.getEmail(), request.getEmail(), "reason=ACCOUNT_LOCKED");
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        } catch (BadCredentialsException e) {
            auditLogService.logWithActor(AuditAction.LOGIN_FAILURE, "USER",
                    request.getEmail(), request.getEmail(), "reason=INVALID_CREDENTIALS");
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = userRepository.findByEmailAndDeletedFalse(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged in: id={} email={}", user.getId(), user.getEmail());
        auditLogService.log(AuditAction.LOGIN_SUCCESS, "USER",
                String.valueOf(user.getId()), "email=" + user.getEmail());

        AuthenticatedPrincipal principal = resolvePrincipal(user);
        SessionTokenBundle tokens = issueSession(principal, clientMetadata, null);
        AuthResponse response = AuthResponse.builder()
                .user(userMapper.toUserResponse(user))
                .tokens(tokens.response())
                .build();
        return new AuthenticatedSessionResponse(response, tokens.refreshToken());
    }

    @Override
    @Transactional
    public TokenRefreshResponse refreshToken(String refreshTokenFromCookie,
                                             String refreshTokenFromBody,
                                             ClientMetadata clientMetadata) {
        String token = resolveRefreshToken(refreshTokenFromCookie, refreshTokenFromBody);

        if (!tokenProvider.validateToken(token)) {
            cleanupExpiredRefreshTokenIfPossible(token);
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (!tokenProvider.isRefreshToken(token)) {
            throw new AppException(ErrorCode.TOKEN_INVALID,
                    "Expected a refresh token, but received an access token");
        }

        JwtTokenProvider.RefreshTokenClaims claims = tokenProvider.extractRefreshTokenClaims(token);
        if (claims == null) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        RefreshSession storedSession = refreshSessionService.getSession(
                claims.principalType(),
                claims.principalId(),
                claims.sessionId()
        );
        if (storedSession == null) {
            refreshSessionService.revokeFamily(claims.principalType(), claims.principalId(), claims.familyId());
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String tokenHash = refreshSessionService.hashToken(token);
        if (!tokenHash.equals(storedSession.getTokenHash())) {
            refreshSessionService.revokeFamily(claims.principalType(), claims.principalId(), claims.familyId());
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        AuthenticatedPrincipal principal = loadPrincipal(claims.principalType(), claims.principalId());
        ensurePrincipalActive(principal);

        refreshSessionService.revokeSession(claims.principalType(), claims.principalId(), claims.sessionId());

        log.info("Token refreshed: principalType={} principalId={} userId={}",
                claims.principalType(), claims.principalId(), principal.user().getId());

        SessionTokenBundle tokens = issueSession(principal, clientMetadata, claims.familyId());
        return new TokenRefreshResponse(tokens.response(), tokens.refreshToken());
    }

    @Override
    public void revokeAllRefreshSessions(AuthPrincipalType principalType, UUID principalId) {
        refreshSessionService.revokeAllRefreshSessions(principalType, principalId);
    }

    private SessionTokenBundle issueSession(AuthenticatedPrincipal principal,
                                            ClientMetadata clientMetadata,
                                            String existingFamilyId) {
        User user = principal.user();
        List<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList());

        String accessToken = tokenProvider.generateAccessToken(user.getEmail(), roleNames);
        String sessionId = UUID.randomUUID().toString();
        String familyId = existingFamilyId != null ? existingFamilyId : UUID.randomUUID().toString();
        String refreshToken = tokenProvider.generateRefreshToken(
                user.getEmail(),
                principal.principalType(),
                principal.principalId(),
                sessionId,
                familyId
        );

        Instant now = Instant.now();
        refreshSessionService.storeSession(
                RefreshSession.builder()
                        .sessionId(sessionId)
                        .familyId(familyId)
                        .principalType(principal.principalType())
                        .principalId(principal.principalId())
                        .tokenHash(refreshSessionService.hashToken(refreshToken))
                        .issuedAt(now)
                        .expiresAt(now.plusMillis(appProperties.getJwt().getRefreshTokenExpiration()))
                        .userAgent(clientMetadata.userAgent())
                        .ipAddress(clientMetadata.ipAddress())
                        .build(),
                Duration.ofMillis(appProperties.getJwt().getRefreshTokenExpiration())
        );

        TokenResponse response = TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(appProperties.getJwt().getAccessTokenExpiration() / 1000)
                .build();
        return new SessionTokenBundle(response, refreshToken);
    }

    private AuthenticatedPrincipal resolvePrincipal(User user) {
        boolean customerOnly = user.getRoles().size() == 1
                && user.getRoles().stream().allMatch(role -> role.getName() == RoleName.CUSTOMER);

        if (customerOnly) {
            Customer customer = customerRepository.findByUserIdAndDeletedFalse(user.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
            return new AuthenticatedPrincipal(AuthPrincipalType.CUSTOMER, customer.getId(), user);
        }

        return new AuthenticatedPrincipal(AuthPrincipalType.USER, user.getId(), user);
    }

    private AuthenticatedPrincipal loadPrincipal(AuthPrincipalType principalType, UUID principalId) {
        if (principalType == AuthPrincipalType.CUSTOMER) {
            Customer customer = customerRepository.findByIdAndDeletedFalse(principalId)
                    .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
            return new AuthenticatedPrincipal(AuthPrincipalType.CUSTOMER, customer.getId(), customer.getUser());
        }

        User user = userRepository.findByIdAndDeletedFalse(principalId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return new AuthenticatedPrincipal(AuthPrincipalType.USER, user.getId(), user);
    }

    private void ensurePrincipalActive(AuthenticatedPrincipal principal) {
        if (principal.user().getStatus() != UserStatus.ACTIVE) {
            refreshSessionService.revokeAllRefreshSessions(principal.principalType(), principal.principalId());
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    private String resolveRefreshToken(String refreshTokenFromCookie, String refreshTokenFromBody) {
        if (refreshTokenFromCookie != null && !refreshTokenFromCookie.isBlank()) {
            return refreshTokenFromCookie;
        }
        if (refreshTokenFromBody != null && !refreshTokenFromBody.isBlank()) {
            return refreshTokenFromBody;
        }
        throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    private void cleanupExpiredRefreshTokenIfPossible(String token) {
        JwtTokenProvider.RefreshTokenClaims claims = tokenProvider.extractRefreshTokenClaimsAllowExpired(token);
        if (claims != null) {
            refreshSessionService.revokeSession(claims.principalType(), claims.principalId(), claims.sessionId());
        }
    }

    private void revokeRefreshTokenIfPossible(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        JwtTokenProvider.RefreshTokenClaims claims = tokenProvider.extractRefreshTokenClaimsAllowExpired(refreshToken);
        if (claims == null) {
            return;
        }

        refreshSessionService.revokeSession(claims.principalType(), claims.principalId(), claims.sessionId());
    }

    private record AuthenticatedPrincipal(AuthPrincipalType principalType, UUID principalId, User user) {
    }

    private record SessionTokenBundle(TokenResponse response, String refreshToken) {
    }
}
