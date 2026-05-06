package com.locnguyen.ecommerce.domains.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.locnguyen.ecommerce.common.config.AppProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.exception.GlobalExceptionHandler;
import com.locnguyen.ecommerce.common.security.RefreshTokenCookieService;
import com.locnguyen.ecommerce.domains.auth.dto.AuthResponse;
import com.locnguyen.ecommerce.domains.auth.dto.LoginRequest;
import com.locnguyen.ecommerce.domains.auth.dto.TokenResponse;
import com.locnguyen.ecommerce.domains.auth.dto.UserResponse;
import com.locnguyen.ecommerce.domains.auth.service.AuthService;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().getRefreshCookie().setName("fashion-shop.refresh-token");
        appProperties.getAuth().getRefreshCookie().setPath("/api/v1/auth");
        appProperties.getAuth().getRefreshCookie().setSameSite("Lax");
        appProperties.getAuth().getRefreshCookie().setSecure(false);
        appProperties.getAuth().getRefreshCookie().setHttpOnly(true);
        appProperties.getAuth().getRefreshCookie().setMaxAge(604800L);

        AuthController controller = new AuthController(
                authService,
                new RefreshTokenCookieService(appProperties)
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void login_sets_refresh_cookie_and_does_not_return_refresh_token_in_body() throws Exception {
        AuthResponse response = AuthResponse.builder()
                .user(UserResponse.builder()
                        .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .email("user@example.com")
                        .status(UserStatus.ACTIVE)
                        .roles(Set.of("CUSTOMER"))
                        .build())
                .tokens(TokenResponse.builder()
                        .accessToken("access-token")
                        .tokenType("Bearer")
                        .expiresIn(3600L)
                        .build())
                .build();
        when(authService.login(any(LoginRequest.class), any()))
                .thenReturn(new AuthService.AuthenticatedSessionResponse(response, "refresh-token"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Passw0rd1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("fashion-shop.refresh-token=refresh-token")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Path=/api/v1/auth")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.data.tokens.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokens.refreshToken").doesNotExist());
    }

    @Test
    void refresh_reads_cookie_rotates_cookie_and_returns_access_token_only() throws Exception {
        when(authService.refreshToken(eq("old-refresh"), isNull(), any()))
                .thenReturn(new AuthService.TokenRefreshResponse(
                        TokenResponse.builder()
                                .accessToken("new-access")
                                .tokenType("Bearer")
                                .expiresIn(3600L)
                                .build(),
                        "new-refresh"
                ));

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new MockCookie("fashion-shop.refresh-token", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("fashion-shop.refresh-token=new-refresh")))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(authService).refreshToken(eq("old-refresh"), isNull(), any());
    }

    @Test
    void invalid_refresh_clears_cookie_and_returns_401() throws Exception {
        when(authService.refreshToken(eq("bad-refresh"), isNull(), any()))
                .thenThrow(new AppException(ErrorCode.REFRESH_TOKEN_INVALID));

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .cookie(new MockCookie("fashion-shop.refresh-token", "bad-refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void logout_clears_cookie_and_is_idempotent_without_authorization_header() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new MockCookie("fashion-shop.refresh-token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(authService).logout(null, "refresh-token");
    }
}
