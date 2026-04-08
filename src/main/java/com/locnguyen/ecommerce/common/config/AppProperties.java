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
}
