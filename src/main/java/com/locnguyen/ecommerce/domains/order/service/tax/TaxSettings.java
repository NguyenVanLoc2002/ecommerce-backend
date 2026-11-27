package com.locnguyen.ecommerce.domains.order.service.tax;

import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tax")
public record TaxSettings(boolean orderEstimatesEnabled, boolean finalizationEnabled, String defaultProductCategory,
                          String shippingCategory, Set<UUID> configurators) {
    public TaxSettings {
        configurators = configurators == null ? Set.of() : Set.copyOf(configurators);
    }
}
