package com.locnguyen.ecommerce.domains.carrier.service;

import com.locnguyen.ecommerce.common.config.CheckoutDeliverySlaProperties;
import com.locnguyen.ecommerce.domains.carrier.dto.DeliveryEstimateResponse;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CheckoutDeliverySla {
    private final Map<Route, DeliveryEstimateResponse> estimates;

    public CheckoutDeliverySla(CheckoutDeliverySlaProperties properties) {
        var configured = new HashMap<Route, DeliveryEstimateResponse>();
        if (properties.enabled()) {
            for (var rule : properties.rules()) {
                var route = Route.of(rule.carrierCode(), rule.serviceCode(), rule.pickupWarehouseId(),
                        rule.destinationCity(), rule.destinationDistrict());
                Integer minimum = rule.minBusinessDays();
                Integer maximum = rule.maxBusinessDays();
                if (!route.complete() || minimum == null || maximum == null || minimum < 0 || maximum < minimum
                        || rule.serviceCode().strip().length() > 100) {
                    throw new IllegalArgumentException("Delivery SLA requires a complete route and an ordered business-day range");
                }
                var estimate = DeliveryEstimateResponse.configuredSla(rule.serviceCode().strip(), minimum, maximum);
                if (configured.putIfAbsent(route, estimate) != null) {
                    throw new IllegalArgumentException("Delivery SLA has duplicate routes for " + route.carrierCode());
                }
            }
        }
        this.estimates = Map.copyOf(configured);
    }

    public Optional<DeliveryEstimateResponse> resolve(
            String carrierCode, String serviceCode, UUID pickupWarehouseId, String city, String district) {
        if (estimates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(estimates.get(Route.of(carrierCode, serviceCode, pickupWarehouseId, city, district)));
    }

    private record Route(String carrierCode, String serviceCode, UUID warehouseId, String city, String district) {
        static Route of(String carrierCode, String serviceCode, UUID warehouseId, String city, String district) {
            return new Route(normalize(carrierCode), normalize(serviceCode), warehouseId, normalize(city), normalize(district));
        }

        boolean complete() {
            return warehouseId != null && !carrierCode.isEmpty() && !serviceCode.isEmpty() && !city.isEmpty() && !district.isEmpty();
        }

        private static String normalize(String value) {
            return value == null ? "" : Normalizer.normalize(value.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        }
    }
}
