package com.locnguyen.ecommerce.common.security;

import com.locnguyen.ecommerce.common.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenCookieService {

    private final AppProperties appProperties;

    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(refreshToken, resolveCookieMaxAge()).toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    public String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        String cookieName = appProperties.getAuth().getRefreshCookie().getName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        AppProperties.RefreshCookie cookie = appProperties.getAuth().getRefreshCookie();

        return ResponseCookie.from(cookie.getName(), value)
                .httpOnly(cookie.isHttpOnly())
                .secure(cookie.isSecure())
                .sameSite(cookie.getSameSite())
                .path(cookie.getPath())
                .maxAge(maxAge)
                .build();
    }

    private Duration resolveCookieMaxAge() {
        long configured = appProperties.getAuth().getRefreshCookie().getMaxAge();
        if (configured >= 0) {
            return Duration.ofSeconds(configured);
        }
        return Duration.ofMillis(appProperties.getJwt().getRefreshTokenExpiration());
    }
}
