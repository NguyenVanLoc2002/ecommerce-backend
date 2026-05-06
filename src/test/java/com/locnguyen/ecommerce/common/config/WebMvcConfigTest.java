package com.locnguyen.ecommerce.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void cors_credentials_config_uses_explicit_origins_not_wildcard() {
        AppProperties appProperties = new AppProperties();
        appProperties.getCors().setAllowedOrigins(new String[]{
                "http://localhost:3000",
                "http://localhost:5173"
        });

        WebMvcConfig config = new WebMvcConfig(appProperties);
        CorsRegistry registry = new CorsRegistry();
        config.addCorsMappings(registry);

        Map<String, CorsConfiguration> configurations =
                (Map<String, CorsConfiguration>) ReflectionTestUtils.invokeMethod(
                        registry,
                        "getCorsConfigurations"
                );

        CorsConfiguration corsConfiguration = configurations.get("/api/**");
        assertThat(corsConfiguration).isNotNull();
        assertThat(corsConfiguration.getAllowCredentials()).isTrue();
        assertThat(corsConfiguration.getAllowedOrigins())
                .contains("http://localhost:3000", "http://localhost:5173")
                .doesNotContain("*");
    }
}
