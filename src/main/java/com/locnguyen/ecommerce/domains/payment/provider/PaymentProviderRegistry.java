package com.locnguyen.ecommerce.domains.payment.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central registry for all {@link PaymentProvider} implementations.
 *
 * <p>Spring auto-discovers every {@code PaymentProvider} bean and injects them
 * here as a list. The registry normalises provider names to uppercase so
 * lookups are case-insensitive.
 */
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderName().toUpperCase(),
                        Function.identity()
                ));
    }

    public Optional<PaymentProvider> find(String providerName) {
        if (providerName == null) return Optional.empty();
        return Optional.ofNullable(providers.get(providerName.toUpperCase()));
    }
}
