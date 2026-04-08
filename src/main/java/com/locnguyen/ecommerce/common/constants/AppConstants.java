package com.locnguyen.ecommerce.common.constants;

/**
 * Application-wide constants.
 */
public final class AppConstants {

    private AppConstants() {}

    // ─── API ────────────────────────────────────────────────────────────────
    public static final String API_V1 = "/api/v1";

    // ─── Pagination ─────────────────────────────────────────────────────────
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // ─── Auth ────────────────────────────────────────────────────────────────
    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    // ─── Roles ──────────────────────────────────────────────────────────────
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_STAFF = "STAFF";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    // ─── Cache keys ─────────────────────────────────────────────────────────
    public static final String CACHE_PRODUCTS = "products";
    public static final String CACHE_CATEGORIES = "categories";
    public static final String CACHE_BRANDS = "brands";

    // ─── Redis key prefixes ──────────────────────────────────────────────────
    public static final String REDIS_REFRESH_TOKEN_PREFIX = "refresh_token:";
    public static final String REDIS_OTP_PREFIX = "otp:";
    public static final String REDIS_BLACKLIST_TOKEN_PREFIX = "blacklist:";

    // ─── Audit source ────────────────────────────────────────────────────────
    public static final String SYSTEM_USER = "system";
}
