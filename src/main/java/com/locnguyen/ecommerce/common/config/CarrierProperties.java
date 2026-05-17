package com.locnguyen.ecommerce.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.carrier")
@Getter
@Setter
public class CarrierProperties {

    /**
     * Enables the dev/test mock carrier provider.
     */
    private boolean mockEnabled = false;

    /**
     * Base64-encoded AES key used to encrypt carrier secrets at the application layer.
     * Must be supplied via environment variable. Never commit a real key to source.
     */
    private String configEncryptionKey;

    /**
     * Public base URL used to derive carrier webhook callback URLs shown in admin setup screens.
     */
    private String webhookPublicBaseUrl;
}
