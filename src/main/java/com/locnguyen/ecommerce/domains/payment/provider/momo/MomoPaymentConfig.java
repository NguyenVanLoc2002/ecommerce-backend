package com.locnguyen.ecommerce.domains.payment.provider.momo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring configuration for the MoMo payment provider.
 *
 * <p>Registers {@link MomoPaymentProperties} for property binding (always).
 * Creates the dedicated {@link RestClient} bean for MoMo API calls only when
 * {@code app.payment.momo.enabled=true}.
 */
@Configuration
@EnableConfigurationProperties(MomoPaymentProperties.class)
public class MomoPaymentConfig {

    /**
     * RestClient wired exclusively to MoMo's API with the configured timeouts.
     * Minimum timeout is 30 000 ms as required by MoMo documentation.
     */
    @Bean("momoRestClient")
    @ConditionalOnProperty(name = "app.payment.momo.enabled", havingValue = "true")
    RestClient momoRestClient(MomoPaymentProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
