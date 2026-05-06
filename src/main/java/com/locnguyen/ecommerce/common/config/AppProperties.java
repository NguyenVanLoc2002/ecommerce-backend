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
}
