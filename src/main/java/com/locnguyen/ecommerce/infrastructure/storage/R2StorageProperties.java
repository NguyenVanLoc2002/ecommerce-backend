package com.locnguyen.ecommerce.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage.r2")
public class R2StorageProperties {

    private boolean enabled = false;
    private String endpoint;
    private String accessKeyId;
    private String secretAccessKey;
    private String bucket;
    private String region = "auto";
    private String publicBaseUrl;
    private String catalogPrefix = "catalog/products";
    private long maxImageSizeBytes = 10 * 1024 * 1024;
    private long presignExpirySeconds = 300;
    private long orphanRetentionHours = 24;
    private long cleanupFixedDelayMs = 300_000;
}
