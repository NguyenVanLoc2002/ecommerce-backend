package com.locnguyen.ecommerce.infrastructure.storage;

import java.time.Instant;

public record PresignedUpload(String url, Instant expiresAt) {
}
