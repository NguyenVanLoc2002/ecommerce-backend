package com.locnguyen.ecommerce.domains.auth.dto;

import java.time.Instant;

public record CustomerSessionResponse(
        String id,
        String userAgent,
        Instant lastRefreshedAt,
        Instant expiresAt,
        boolean current
) {
}
