package com.locnguyen.ecommerce.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Auth auth = new Auth();
    private Security security = new Security();
    private Otp otp = new Otp();
    private ResetToken resetToken = new ResetToken();
    private Idempotency idempotency = new Idempotency();
    private Payment payment = new Payment();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenExpiration = 3_600_000L;   // 1 hour
        private long refreshTokenExpiration = 604_800_000L; // 7 days
    }

    @Getter
    @Setter
    public static class Cors {
        private String[] allowedOrigins = {"http://localhost:3000"};
    }

    @Getter
    @Setter
    public static class Auth {
        private RefreshCookie refreshCookie = new RefreshCookie();
    }

    @Getter
    @Setter
    public static class RefreshCookie {
        private String name = "fashion-shop.refresh-token";
        private String path = "/api/v1/auth";
        private String sameSite = "Lax";
        private boolean secure = false;
        private boolean httpOnly = true;
        private long maxAge = -1L;
    }

    /**
     * Generic security toggles independent of the JWT/cookie subsystem.
     */
    @Getter
    @Setter
    public static class Security {
        /**
         * When {@code false} (default and recommended), the refresh-token endpoint
         * accepts the refresh token only from the HttpOnly cookie. When {@code true},
         * a deprecated request-body fallback remains active for backward compatibility.
         */
        private boolean refreshTokenBodyFallbackEnabled = false;

        /**
         * When {@code true}, the CSRF double-submit cookie validator is enforced for
         * cookie-mutating endpoints (refresh-token / logout). Disabled by default in
         * dev environments to avoid breaking existing clients during the rollout.
         */
        private boolean csrfDoubleSubmitEnabled = false;
    }

    @Getter
    @Setter
    public static class Otp {
        private int expiresMinutes = 5;
        private int maxAttempts = 5;
        private int resendCooldownSeconds = 60;
        private int sendLimitWindowMinutes = 15;
        private int sendLimitMax = 5;
    }

    @Getter
    @Setter
    public static class ResetToken {
        private int expiresMinutes = 10;
    }

    @Getter
    @Setter
    public static class Idempotency {
        /** TTL for idempotency records. Requests older than this threshold are stale. */
        private long ttlHours = 24L;
        /** Processing records older than this (minutes) are treated as stale and allow retry. */
        private long staleProcessingMinutes = 5L;
    }

    @Getter
    @Setter
    public static class Payment {
        /**
         * Base URL the application is reachable at — used to build the webhook callback
         * URL passed to each payment gateway (e.g., {@code https://api.example.com}).
         * Must NOT end with a slash.
         */
        private String baseCallbackUrl = "http://localhost:8080/api/v1/payments";
        /**
         * Default URL to redirect the customer to after payment (success or failure page).
         * Can be overridden per-request via {@code returnUrl} in {@link
         * com.locnguyen.ecommerce.domains.payment.dto.InitPaymentRequest}.
         */
        private String defaultReturnUrl = "http://localhost:3000/payment/result";
    }
}
