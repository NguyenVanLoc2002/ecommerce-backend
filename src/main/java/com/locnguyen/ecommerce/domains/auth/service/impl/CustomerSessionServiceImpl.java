package com.locnguyen.ecommerce.domains.auth.service.impl;

import com.locnguyen.ecommerce.common.config.AppProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.security.AuthPrincipalType;
import com.locnguyen.ecommerce.common.security.JwtTokenProvider;
import com.locnguyen.ecommerce.common.security.RefreshSession;
import com.locnguyen.ecommerce.common.security.RefreshSessionService;
import com.locnguyen.ecommerce.domains.auth.dto.CustomerSessionResponse;
import com.locnguyen.ecommerce.domains.auth.service.CustomerSessionService;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerSessionServiceImpl implements CustomerSessionService {

    private static final int MAX_USER_AGENT_LENGTH = 256;

    private final UserService userService;
    private final RefreshSessionService refreshSessionService;
    private final JwtTokenProvider tokenProvider;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerSessionResponse> listCurrentCustomerSessions(String refreshTokenFromCookie) {
        UUID customerId = activeCustomer().getId();
        List<RefreshSession> sessions = activeSessions(customerId);
        String currentFamily = findCurrentFamily(refreshTokenFromCookie, customerId, sessions);

        return sessions.stream()
                .sorted(Comparator.comparing(RefreshSession::getIssuedAt).reversed())
                .collect(Collectors.toMap(RefreshSession::getFamilyId, session -> session, (first, second) -> first))
                .values()
                .stream()
                .map(session -> new CustomerSessionResponse(
                        session.getFamilyId(),
                        displayUserAgent(session.getUserAgent()),
                        session.getIssuedAt(),
                        session.getExpiresAt(),
                        session.getFamilyId().equals(currentFamily)))
                .sorted(Comparator.comparing(CustomerSessionResponse::current).reversed()
                        .thenComparing(CustomerSessionResponse::lastRefreshedAt, Comparator.reverseOrder())
                        .thenComparing(CustomerSessionResponse::id))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void revokeSession(UUID sessionId, String refreshTokenFromCookie) {
        revokeMatchingSessions(refreshTokenFromCookie, sessionId.toString());
    }

    @Override
    @Transactional(readOnly = true)
    public void revokeOtherSessions(String refreshTokenFromCookie) {
        revokeMatchingSessions(refreshTokenFromCookie, null);
    }

    private void revokeMatchingSessions(String currentCookie, String targetFamilyId) {
        Customer customer = activeCustomer();
        UUID customerId = customer.getId();
        List<RefreshSession> sessions = activeSessions(customerId);
        String currentFamilyId = findCurrentFamily(currentCookie, customerId, sessions);
        if (currentFamilyId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "An active current session cookie is required");
        }
        if (currentFamilyId.equals(targetFamilyId)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Use logout to end the current session");
        }

        long accessTtl = appProperties.getJwt().getAccessTokenExpiration();
        long refreshTtl = appProperties.getJwt().getRefreshTokenExpiration();
        Duration familyTtl = Duration.ofMillis(Math.max(accessTtl, refreshTtl));
        Duration legacyAccessTtl = Duration.ofMillis(accessTtl);
        refreshSessionService.listKnownCustomerFamilies(customerId).stream()
                .filter(familyId -> !familyId.equals(currentFamilyId)
                        && !refreshSessionService.isCustomerFamilyRevoked(familyId)
                        && (targetFamilyId == null || targetFamilyId.equals(familyId)))
                .distinct()
                .forEach(familyId -> refreshSessionService.revokeCustomerFamily(
                        customerId, customer.getUser().getEmail(), familyId, familyTtl, legacyAccessTtl));
    }

    private Customer activeCustomer() {
        Customer customer = userService.getCurrentCustomer();
        if (customer.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        return customer;
    }

    private List<RefreshSession> activeSessions(UUID customerId) {
        List<RefreshSession> sessions = refreshSessionService.listActiveSessions(AuthPrincipalType.CUSTOMER, customerId).stream()
                .filter(session -> !refreshSessionService.isCustomerFamilyRevoked(session.getFamilyId()))
                .toList();
        sessions.forEach(refreshSessionService::indexCustomerFamily);
        return sessions;
    }

    private String findCurrentFamily(String token, UUID customerId, List<RefreshSession> sessions) {
        if (token == null || token.isBlank()) {
            return null;
        }
        JwtTokenProvider.RefreshTokenClaims claims = tokenProvider.extractRefreshTokenClaimsAllowExpired(token);
        if (claims == null || claims.expiresAt() == null || !claims.expiresAt().toInstant().isAfter(Instant.now())
                || claims.principalType() != AuthPrincipalType.CUSTOMER
                || !customerId.equals(claims.principalId())) {
            return null;
        }
        String tokenHash = refreshSessionService.hashToken(token);
        return sessions.stream()
                .filter(session -> claims.sessionId().equals(session.getSessionId())
                        && claims.familyId().equals(session.getFamilyId())
                        && tokenHash.equals(session.getTokenHash()))
                .map(RefreshSession::getFamilyId)
                .findFirst()
                .orElse(null);
    }

    private String displayUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        String printable = userAgent.substring(0, Math.min(userAgent.length(), MAX_USER_AGENT_LENGTH))
                .replaceAll("\\p{Cntrl}", " ").strip();
        return printable.isEmpty() ? null : printable;
    }
}
