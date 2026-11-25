package com.locnguyen.ecommerce.common.security;

import com.locnguyen.ecommerce.common.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerSessionAuthenticationTest {

    private static final String EMAIL = "customer@example.test";

    private final AppProperties properties = new AppProperties();
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(properties);
    private final RefreshSessionService refreshSessions = mock(RefreshSessionService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            tokenProvider, mock(TokenBlacklistService.class), refreshSessions);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accessTokensAreRejectedImmediatelyForRevokedFamily() throws Exception {
        properties.getJwt().setSecret("local-unit-test-signature-key-only".repeat(2));
        String otherDeviceAccess = tokenProvider.generateAccessToken(EMAIL, List.of("CUSTOMER"), "other-device");
        String currentAccess = tokenProvider.generateAccessToken(EMAIL, List.of("CUSTOMER"), "current-device");
        when(refreshSessions.isCustomerAccessRevoked(EMAIL, "other-device")).thenReturn(true);

        assertTokenAuthentication(otherDeviceAccess, false);
        assertTokenAuthentication(currentAccess, true);
    }

    @Test
    void legacyAccessGetsRefreshedAfterAnyDeviceIsRevoked() throws Exception {
        properties.getJwt().setSecret("local-unit-test-signature-key-only".repeat(2));
        String legacyAccess = tokenProvider.generateAccessToken(EMAIL, List.of("CUSTOMER"));
        when(refreshSessions.isCustomerAccessRevoked(EMAIL, null)).thenReturn(true);

        assertTokenAuthentication(legacyAccess, false);
    }

    private void assertTokenAuthentication(String token, boolean expected) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), (req, response) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication != null && authentication.isAuthenticated()).isEqualTo(expected);
        });
    }
}
