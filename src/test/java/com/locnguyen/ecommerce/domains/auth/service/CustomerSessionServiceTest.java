package com.locnguyen.ecommerce.domains.auth.service;

import com.locnguyen.ecommerce.common.config.AppProperties;
import com.locnguyen.ecommerce.common.security.AuthPrincipalType;
import com.locnguyen.ecommerce.common.security.JwtTokenProvider;
import com.locnguyen.ecommerce.common.security.RefreshSession;
import com.locnguyen.ecommerce.common.security.RefreshSessionService;
import com.locnguyen.ecommerce.domains.auth.dto.CustomerSessionResponse;
import com.locnguyen.ecommerce.domains.auth.service.impl.CustomerSessionServiceImpl;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSessionServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("e4d0e8af-69c0-48f0-a2ac-b15accf9e90b");
    private static final Instant NOW = Instant.now();

    @Mock UserService userService;
    @Mock RefreshSessionService refreshSessionService;
    @Mock JwtTokenProvider tokenProvider;
    @Spy AppProperties appProperties = new AppProperties();
    @InjectMocks CustomerSessionServiceImpl service;

    @BeforeEach
    void currentCustomer() {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);
        User user = new User();
        user.setEmail("customer@example.test");
        customer.setUser(user);
        when(userService.getCurrentCustomer()).thenReturn(customer);
    }

    @Test
    void currentCookieIdentifiesItsFamilyAndRotatedRecordsCollapseIntoOneRow() {
        when(refreshSessionService.listActiveSessions(AuthPrincipalType.CUSTOMER, CUSTOMER_ID))
                .thenReturn(List.of(
                        session("current-old", "current", NOW.minusSeconds(30), null),
                        session("current-token", "current", NOW.minusSeconds(10), "Browser\nclient"),
                        session("other-token", "other", NOW, null)));
        when(tokenProvider.extractRefreshTokenClaimsAllowExpired("cookie")).thenReturn(claims(CUSTOMER_ID));
        when(refreshSessionService.hashToken("cookie")).thenReturn("hash");

        assertThat(service.listCurrentCustomerSessions("cookie"))
                .extracting(CustomerSessionResponse::id)
                .containsExactly("current", "other");
        CustomerSessionResponse current = service.listCurrentCustomerSessions("cookie").get(0);
        assertThat(current.current()).isTrue();
        assertThat(current.userAgent()).isEqualTo("Browser client");
        assertThat(current.lastRefreshedAt()).isEqualTo(NOW.minusSeconds(10));
    }

    @Test
    void aCookieFromAnotherAccountDoesNotIdentifyADevice() {
        when(refreshSessionService.listActiveSessions(AuthPrincipalType.CUSTOMER, CUSTOMER_ID))
                .thenReturn(List.of(session("current-token", "current", NOW, null)));
        when(tokenProvider.extractRefreshTokenClaimsAllowExpired("cookie")).thenReturn(claims(UUID.randomUUID()));

        assertThat(service.listCurrentCustomerSessions("cookie"))
                .extracting(CustomerSessionResponse::current)
                .containsExactly(false);
        verify(refreshSessionService, never()).hashToken("cookie");
    }

    @Test
    void bulkRevocationIncludesOtherDeviceWhileItsRefreshRecordRotates() {
        when(refreshSessionService.listActiveSessions(AuthPrincipalType.CUSTOMER, CUSTOMER_ID))
                .thenReturn(List.of(session("current-token", "current", NOW, null)));
        when(tokenProvider.extractRefreshTokenClaimsAllowExpired("cookie")).thenReturn(claims(CUSTOMER_ID));
        when(refreshSessionService.hashToken("cookie")).thenReturn("hash");
        when(refreshSessionService.listKnownCustomerFamilies(CUSTOMER_ID)).thenReturn(List.of("current", "rotating"));

        service.revokeOtherSessions("cookie");

        Duration refreshTtl = Duration.ofMillis(appProperties.getJwt().getRefreshTokenExpiration());
        Duration accessTtl = Duration.ofMillis(appProperties.getJwt().getAccessTokenExpiration());
        verify(refreshSessionService).revokeCustomerFamily(CUSTOMER_ID, "customer@example.test", "rotating",
                refreshTtl, accessTtl);
        verify(refreshSessionService, never()).revokeCustomerFamily(CUSTOMER_ID, "customer@example.test", "current",
                refreshTtl, accessTtl);
    }

    private static JwtTokenProvider.RefreshTokenClaims claims(UUID customerId) {
        return new JwtTokenProvider.RefreshTokenClaims(
                "customer@example.test", AuthPrincipalType.CUSTOMER, customerId, "current-token", "current",
                Date.from(NOW.minusSeconds(10)), Date.from(NOW.plusSeconds(300)));
    }

    private static RefreshSession session(String id, String family, Instant issuedAt, String userAgent) {
        return RefreshSession.builder()
                .sessionId(id).familyId(family).issuedAt(issuedAt).expiresAt(NOW.plusSeconds(300))
                .tokenHash("hash").userAgent(userAgent)
                .build();
    }
}
