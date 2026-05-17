package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.carrier.ahamove")
public class AhamoveProperties {

    private boolean enabled = false;
    private String baseUrl = "https://partner-apistg.ahamove.com";
    private String apiKey = "";
    private String phone = "";
    private String brandName = "Locen Studio";
    private String webhookToken = "";
    private String groupServiceId = "BIKE";
    private String paymentMethod = "CASH";
    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 30_000;
}
