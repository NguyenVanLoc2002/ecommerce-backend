package com.locnguyen.ecommerce.domains.carrier.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CarrierProviderRegistry {

    private final Map<String, CarrierProvider> providers;

    public CarrierProviderRegistry(List<CarrierProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        provider -> provider.getProviderType().toUpperCase(),
                        Function.identity()
                ));
    }

    public Optional<CarrierProvider> find(String providerType) {
        if (providerType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(providerType.toUpperCase()));
    }
}
