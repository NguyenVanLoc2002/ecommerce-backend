package com.locnguyen.ecommerce.common.security;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless JWT authentication filter — runs once per request.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Extract {@code Bearer} token from the {@code Authorization} header.</li>
 *   <li>Validate token signature and expiration via {@link JwtTokenProvider}.</li>
 *   <li>Reject refresh tokens used as access tokens.</li>
 *   <li>Build {@link UsernamePasswordAuthenticationToken} from token claims
 *       (username + roles) — <b>no database lookup</b>.</li>
 *   <li>Set the {@code Authentication} in {@link SecurityContextHolder}.</li>
 * </ol>
 *
 * <p>If the token is absent or invalid, the filter does NOT write a response.
 * It lets the request proceed unauthenticated; the security filter chain's
 * {@link JwtAuthenticationEntryPoint} will return 401 if the endpoint is protected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (token != null) {
            authenticate(token, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        if (!tokenProvider.validateToken(token)) {
            // Invalid / expired — let SecurityConfig's entry point handle 401
            return;
        }

        if (tokenBlacklistService.isBlacklisted(token)) {
            log.warn("Blacklisted (logged-out) token rejected — path={}", request.getRequestURI());
            return;
        }

        // Reject refresh tokens used as access tokens
        if (!tokenProvider.isAccessToken(token)) {
            log.warn("Refresh token used as access token — path={}", request.getRequestURI());
            return;
        }

        String username = tokenProvider.extractUsername(token);
        List<String> roles = tokenProvider.extractRoles(token);

        // Build authorities — Spring Security expects "ROLE_" prefix
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);

        // Attach request details (remote IP, session) for audit / intrusion detection
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Authenticated user='{}' roles={} path={}",
                username, roles, request.getRequestURI());
    }

    /**
     * Extract the raw JWT from the {@code Authorization: Bearer <token>} header.
     *
     * @return token string, or {@code null} if the header is absent or malformed
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AppConstants.AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(AppConstants.BEARER_PREFIX)) {
            return header.substring(AppConstants.BEARER_PREFIX.length());
        }
        return null;
    }
}
