package com.locnguyen.ecommerce.domains.auth.service;

import com.locnguyen.ecommerce.common.config.AppProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.security.JwtTokenProvider;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.auth.dto.*;
import com.locnguyen.ecommerce.domains.auth.mapper.UserMapper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication service — handles register, login, and token refresh.
 *
 * <p>Token strategy:
 * <ul>
 *   <li>Access token carries {@code sub} (email) + {@code roles} claim — validated
 *       per-request by {@link com.locnguyen.ecommerce.common.security.JwtAuthenticationFilter}</li>
 *   <li>Refresh token carries only {@code sub} — exchanged for a new token pair</li>
 *   <li>Tokens are currently stateless (self-contained JWT). Token revocation via
 *       Redis blacklist will be added when logout is implemented.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CustomerRepository customerRepository;
    private final UserMapper userMapper;
    private final AppProperties appProperties;
    private final AuditLogService auditLogService;

    // ─── Register ────────────────────────────────────────────────────────────

    /**
     * Register a new customer account.
     *
     * <ol>
     *   <li>Validate email uniqueness</li>
     *   <li>Validate phone uniqueness (if provided)</li>
     *   <li>Hash password with BCrypt</li>
     *   <li>Assign CUSTOMER role</li>
     *   <li>Persist user</li>
     *   <li>Generate tokens (auto-login)</li>
     * </ol>
     *
     * @return auth response with user profile and token pair
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Business validation
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (request.getPhoneNumber() != null
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        // Resolve CUSTOMER role from seed data
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException(
                        "CUSTOMER role not found — check Flyway V2 seed data"));

        // Build user entity
        User user = new User();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName() != null ? request.getLastName().trim() : null);
        user.setPhoneNumber(request.getPhoneNumber());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(customerRole));

        user = userRepository.save(user);

        // Create customer profile for the new user
        Customer customer = new Customer(user);
        customerRepository.save(customer);

        log.info("User registered: id={} email={} customerId={}", user.getId(), user.getEmail(), customer.getId());
        auditLogService.log(AuditAction.USER_REGISTERED, "USER",
                String.valueOf(user.getId()), "email=" + user.getEmail());

        // Auto-login: generate tokens so the client can start immediately
        TokenResponse tokens = generateTokenPair(user);
        return AuthResponse.builder()
                .user(userMapper.toUserResponse(user))
                .tokens(tokens)
                .build();
    }

    // ─── Login ───────────────────────────────────────────────────────────────

    /**
     * Authenticate with email and password.
     *
     * <p>Delegates credential verification to Spring Security's
     * {@link AuthenticationManager}, which uses
     * {@link com.locnguyen.ecommerce.common.security.CustomUserDetailsService}.
     *
     * <p>Error mapping:
     * <ul>
     *   <li>wrong email/password → {@code INVALID_CREDENTIALS}</li>
     *   <li>disabled account → {@code ACCOUNT_DISABLED}</li>
     *   <li>locked account → {@code ACCOUNT_DISABLED}</li>
     * </ul>
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
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

        // Load full user entity (roles are eagerly fetched)
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Update last login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged in: id={} email={}", user.getId(), user.getEmail());
        auditLogService.log(AuditAction.LOGIN_SUCCESS, "USER",
                String.valueOf(user.getId()), "email=" + user.getEmail());

        TokenResponse tokens = generateTokenPair(user);
        return AuthResponse.builder()
                .user(userMapper.toUserResponse(user))
                .tokens(tokens)
                .build();
    }

    // ─── Refresh Token ───────────────────────────────────────────────────────

    /**
     * Exchange a valid refresh token for a new access + refresh token pair.
     *
     * <p>Validates:
     * <ul>
     *   <li>Token signature and expiration</li>
     *   <li>Token type is {@code refresh} (rejects access tokens)</li>
     *   <li>User exists and is active</li>
     * </ul>
     *
     * @return new token pair
     */
    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();

        // Validate token integrity
        if (!tokenProvider.validateToken(token)) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // Must be a refresh token — reject access tokens
        if (!tokenProvider.isRefreshToken(token)) {
            throw new AppException(ErrorCode.TOKEN_INVALID,
                    "Expected a refresh token, but received an access token");
        }

        // Load user from subject claim
        String email = tokenProvider.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }

        log.info("Token refreshed for user: id={} email={}", user.getId(), user.getEmail());

        return generateTokenPair(user);
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private TokenResponse generateTokenPair(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList());

        String accessToken = tokenProvider.generateAccessToken(user.getEmail(), roleNames);
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(appProperties.getJwt().getAccessTokenExpiration() / 1000)
                .build();
    }
}
