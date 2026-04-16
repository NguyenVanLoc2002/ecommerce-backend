package com.locnguyen.ecommerce.common.filter;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that runs on every request to:
 * <ol>
 *   <li>Attach a {@code X-Request-ID} correlation ID (use the incoming header value
 *       if the caller sent one, otherwise generate a fresh UUID)</li>
 *   <li>Populate MDC with request metadata so every log line produced during
 *       the request automatically carries the correlation ID, user, method and URI</li>
 *   <li>Log one ACCESS line per request: method, URI, status code, and wall-clock
 *       duration in milliseconds</li>
 * </ol>
 *
 * <p>Runs at {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE} so that
 * the MDC context is available to all subsequent filters (including Spring Security)
 * and the full request duration is measured.
 *
 * <p>Username extraction works by reading the Bearer JWT directly from the
 * {@code Authorization} header — this avoids a race with
 * {@code SecurityContextHolderFilter}, which clears the context before this
 * filter's {@code finally} block would otherwise run.
 */
@Slf4j
@Component
@Order(Integer.MIN_VALUE)
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    private static final String HEALTH_PATH = "/actuator";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // ── 1. Correlation ID ──────────────────────────────────────────────
        String requestId = resolveRequestId(request);
        MDC.put(AppConstants.MDC_REQUEST_ID, requestId);
        response.addHeader(AppConstants.HEADER_REQUEST_ID, requestId);

        // ── 2. Extract caller from JWT (before security filter clears ctx) ─
        String user = extractUserFromToken(request);
        if (user != null) {
            MDC.put(AppConstants.MDC_USER, user);
        }

        MDC.put(AppConstants.MDC_METHOD, request.getMethod());
        MDC.put(AppConstants.MDC_URI, request.getRequestURI());

        long start = System.currentTimeMillis();

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();

            MDC.put(AppConstants.MDC_STATUS, String.valueOf(status));
            MDC.put(AppConstants.MDC_DURATION, duration + "ms");

            log.info("ACCESS {} {} {} {}ms", request.getMethod(),
                    request.getRequestURI(), status, duration);

            MDC.clear();
        }
    }

    /** Skip verbose access logging for actuator health probes. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith(HEALTH_PATH);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader(AppConstants.HEADER_REQUEST_ID);
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }

    private String extractUserFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AppConstants.AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(AppConstants.BEARER_PREFIX)) {
            return null;
        }
        try {
            String token = authHeader.substring(AppConstants.BEARER_PREFIX.length());
            if (jwtTokenProvider.validateToken(token) && jwtTokenProvider.isAccessToken(token)) {
                return jwtTokenProvider.extractUsername(token);
            }
        } catch (Exception ignored) {
            // Malformed or expired tokens are handled by JwtAuthenticationFilter;
            // do not pollute the log here.
        }
        return null;
    }
}
