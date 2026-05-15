package com.locnguyen.ecommerce.infrastructure.payment.paypal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring configuration for the PayPal payment provider.
 *
 * <p>Registers {@link PaypalPaymentProperties} for property binding (always).
 * Creates the dedicated {@link RestClient} bean for PayPal API calls only when
 * {@code app.payment.paypal.enabled=true}.
 */
@Configuration
@EnableConfigurationProperties(PaypalPaymentProperties.class)
public class PaypalPaymentConfig {

    /**
     * RestClient wired exclusively to PayPal's API with the configured timeouts.
     * Minimum timeout is 30 000 ms as required by the provider spec.
     */
    @Bean("paypalRestClient")
    @ConditionalOnProperty(name = "app.payment.paypal.enabled", havingValue = "true")
    RestClient paypalRestClient(PaypalPaymentProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
