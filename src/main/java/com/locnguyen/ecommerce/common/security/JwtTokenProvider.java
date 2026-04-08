package com.locnguyen.ecommerce.common.security;

import com.locnguyen.ecommerce.common.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Stateless JWT token generation and validation.
 *
 * <p>Token design:
 * <ul>
 *   <li><b>Access token</b> — contains {@code sub} (username) + {@code roles} claim.
 *       Short-lived (default 1 hour). Used by API clients on every request.</li>
 *   <li><b>Refresh token</b> — contains only {@code sub}.
 *       Long-lived (default 7 days). Used solely to obtain a new access token.</li>
 * </ul>
 *
 * <p>The filter chain ({@link JwtAuthenticationFilter}) validates access tokens and builds
 * the {@code Authentication} from claims — no database lookup is performed per request.
 *
 * <p><b>Production secret requirement:</b> The JWT secret must be at least 256 bits (32 bytes).
 * Generate with: {@code openssl rand -base64 64}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String ROLES_CLAIM = "roles";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final AppProperties appProperties;

    // ─── Token generation ────────────────────────────────────────────────────

    /**
     * Generate a signed access token carrying the user's roles.
     *
     * @param username authenticated user's identifier (typically email)
     * @param roles    list of role names WITHOUT "ROLE_" prefix (e.g., ["ADMIN", "STAFF"])
     */
    public String generateAccessToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + appProperties.getJwt().getAccessTokenExpiration());

        return Jwts.builder()
                .subject(username)
                .claim(ROLES_CLAIM, roles)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    /**
     * Generate a signed refresh token — no roles, just the subject.
     *
     * @param username authenticated user's identifier
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + appProperties.getJwt().getRefreshTokenExpiration());

        return Jwts.builder()
                .subject(username)
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    // ─── Validation ──────────────────────────────────────────────────────────

    /**
     * Validate token signature and expiration.
     *
     * @param token raw JWT string
     * @return {@code true} if token is valid and not expired
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
        } catch (io.jsonwebtoken.security.SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty or null: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Returns {@code true} if the token is structurally valid but expired.
     * Used by the auth service to allow refresh-token-based access token renewal.
     */
    public boolean isTokenExpired(String token) {
        try {
            parseClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns {@code true} if this token was issued as an access token.
     */
    public boolean isAccessToken(String token) {
        try {
            return ACCESS_TOKEN_TYPE.equals(parseClaims(token).get(TOKEN_TYPE_CLAIM));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns {@code true} if this token was issued as a refresh token.
     */
    public boolean isRefreshToken(String token) {
        try {
            return REFRESH_TOKEN_TYPE.equals(parseClaims(token).get(TOKEN_TYPE_CLAIM));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ─── Claims extraction ───────────────────────────────────────────────────

    /**
     * Extract the username (subject) from a valid token.
     *
     * @param token a token for which {@link #validateToken(String)} returned {@code true}
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extract the roles list from a valid access token.
     * Returns an empty list for refresh tokens (which carry no roles).
     *
     * @param token a token for which {@link #validateToken(String)} returned {@code true}
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = parseClaims(token).get(ROLES_CLAIM);
        if (roles instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    /**
     * Get the expiration date of a valid token.
     */
    public Date extractExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    /**
     * Extract claims from an expired token without throwing.
     * Used by the auth service to identify the subject of an expired access token
     * during the refresh flow.
     *
     * @return claims if token is expired, {@code null} if token is invalid for other reasons
     */
    public Claims extractExpiredClaims(String token) {
        try {
            parseClaims(token);
            return null; // token is not expired
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(
                appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }
}
