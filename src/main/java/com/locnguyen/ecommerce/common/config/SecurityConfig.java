package com.locnguyen.ecommerce.common.config;

import com.locnguyen.ecommerce.common.security.CsrfDoubleSubmitFilter;
import com.locnguyen.ecommerce.common.security.JwtAccessDeniedHandler;
import com.locnguyen.ecommerce.common.security.JwtAuthenticationEntryPoint;
import com.locnguyen.ecommerce.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central security configuration for the application.
 *
 * <h2>Authentication model</h2>
 * Stateless JWT — protected API requests carry a valid Bearer token in the
 * {@code Authorization} header, while refresh-token rotation uses an HttpOnly
 * cookie only on the auth endpoints.
 *
 * <h2>Role hierarchy</h2>
 * <pre>
 *   SUPER_ADMIN → ADMIN → STAFF → CUSTOMER
 * </pre>
 * ADMIN automatically inherits STAFF permissions. STAFF inherits CUSTOMER permissions.
 * This avoids redundant role checks across the codebase.
 *
 * <h2>Endpoint access matrix</h2>
 * <pre>
 *   Public (no token)            → /api/v1/auth/**, GET /api/v1/products/**, swagger, health
 *                                   POST /api/v1/payments/callback (gateway callback)
 *   Authenticated (any role)     → /api/v1/cart/**, /api/v1/orders/**, /api/v1/profile/**
 *   ADMIN / SUPER_ADMIN only     → /api/v1/admin/**
 *   STAFF / ADMIN / SUPER_ADMIN  → [handled via @PreAuthorize on individual endpoints]
 * </pre>
 *
 * <h2>Exception handling layers</h2>
 * <ul>
 *   <li>Filter chain 401 → {@link JwtAuthenticationEntryPoint}</li>
 *   <li>Filter chain 403 → {@link JwtAccessDeniedHandler}</li>
 *   <li>Controller/Service exceptions → {@code GlobalExceptionHandler}</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CsrfDoubleSubmitFilter csrfDoubleSubmitFilter;

    // ─── Endpoint whitelists ─────────────────────────────────────────────────

    /** Fully public — no token required. */
    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/logout",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/forgot/verify",
            "/api/v1/auth/password/reset",
            // Payment gateway callback — called server-to-server by the provider.
            // No bearer token is available on the gateway side.
            // IMPORTANT: HMAC/signature verification inside processCallback is the
            // only guard against spoofed callbacks until it is fully implemented.
            "/api/v1/payments/callback",
            // Payment webhook — provider-initiated server-to-server calls; no bearer token.
            // Security relies on HMAC/signature verification inside the handler.
            "/api/v1/webhooks/payment/**",
            // Carrier webhook — provider-initiated server-to-server calls; no bearer token.
            "/api/v1/shipments/webhook/ahamove",
    };

    /** Public GET-only endpoints for product browsing and dev tooling. */
    private static final String[] PUBLIC_GET = {
            "/api/v1/products/**",
            "/api/v1/categories/**",
            "/api/v1/brands/**",
            // Mock payment completion — dev/test only, enabled via app.payment.mock.enabled
            "/api/v1/payments/mock/**",
    };

    /** Developer tooling and open actuator endpoints. */
    private static final String[] OPEN_ENDPOINTS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",  // liveness probe — always public
            "/actuator/info",    // basic build info — public
    };

    /** Actuator management endpoints — restricted to admins. */
    private static final String ACTUATOR_SENSITIVE = "/actuator/**";

    // ─── Security filter chain ───────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — this API remains stateless; auth-cookie refresh relies on
                // SameSite/path/origin restrictions rather than server-side CSRF tokens.
                .csrf(AbstractHttpConfigurer::disable)

                // CORS — delegates to WebMvcConfig.addCorsMappings()
                .cors(Customizer.withDefaults())

                // Stateless session — no HttpSession is created or used
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── Exception handling ─────────────────────────────────────
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))

                // ── Authorization rules ────────────────────────────────────
                .authorizeHttpRequests(auth -> auth

                        // Public — no authentication required
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(OPEN_ENDPOINTS).permitAll()

                        // Sensitive actuator endpoints — admin only
                        .requestMatchers(ACTUATOR_SENSITIVE).hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // Admin management API — STAFF and above
                        // Role hierarchy: SUPER_ADMIN > ADMIN > STAFF
                        // Fine-grained restrictions (e.g. ADMIN-only destructive ops)
                        // are enforced via @PreAuthorize on individual endpoints.
                        .requestMatchers("/api/v1/admin/**")
                        .hasAnyRole("STAFF", "ADMIN", "SUPER_ADMIN")

                        // All other endpoints require a valid token (any role)
                        .anyRequest().authenticated()
                )

                // ── CSRF double-submit (gated) ─────────────────────────────
                // Issues / validates the XSRF-TOKEN cookie + X-XSRF-TOKEN header
                // pair on cookie-mutating endpoints. Toggleable via
                // app.security.csrf-double-submit-enabled. Registered before the
                // JWT filter so unauthenticated cookie-only flows
                // (refresh-token / logout) are still protected.
                .addFilterBefore(csrfDoubleSubmitFilter,
                        UsernamePasswordAuthenticationFilter.class)

                // ── JWT filter ─────────────────────────────────────────────
                // Placed before UsernamePasswordAuthenticationFilter to validate
                // the Bearer token and populate SecurityContext before any
                // form-based auth filter runs (which we don't use, but for ordering correctness).
                // Registered after CSRF — both anchor to UsernamePasswordAuthenticationFilter,
                // and the JWT filter ends up last (closer to the anchor).
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ─── Role hierarchy ──────────────────────────────────────────────────────

    /**
     * Defines the role hierarchy used by both the filter chain and method security.
     *
     * <pre>
     *   ROLE_SUPER_ADMIN → ROLE_ADMIN → ROLE_STAFF → ROLE_CUSTOMER
     * </pre>
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy(
                "ROLE_SUPER_ADMIN > ROLE_ADMIN\n" +
                "ROLE_ADMIN > ROLE_STAFF\n" +
                "ROLE_STAFF > ROLE_CUSTOMER"
        );
    }

    /**
     * Applies role hierarchy to {@code @PreAuthorize} / {@code @PostAuthorize} expressions.
     *
     * <p>Without this, method security ignores the hierarchy — an ADMIN user would fail
     * a {@code hasRole('STAFF')} check even though ADMIN &gt; STAFF in the hierarchy.
     */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler =
                new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    // ─── Core security beans ─────────────────────────────────────────────────

    /**
     * BCrypt password encoder — strength 12 (default 10 may be too fast for production).
     * Used by the auth service when registering and verifying user passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Exposes the {@link AuthenticationManager} as a bean for use in the auth service.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
