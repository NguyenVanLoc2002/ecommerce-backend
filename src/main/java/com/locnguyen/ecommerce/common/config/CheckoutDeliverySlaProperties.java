package com.locnguyen.ecommerce.common.config;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.shipping.delivery-sla")
public record CheckoutDeliverySlaProperties(boolean enabled, List<Rule> rules) {
    public CheckoutDeliverySlaProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public record Rule(
            String carrierCode,
            String serviceCode,
            UUID pickupWarehouseId,
            String destinationCity,
            String destinationDistrict,
            Integer minBusinessDays,
            Integer maxBusinessDays) {}
}
