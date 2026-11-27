package com.locnguyen.ecommerce.domains.carrier.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.dto.CheckoutCarrierOptionResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.CheckoutCarrierQuoteResponse;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProvider;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderRegistry;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingRateRequest;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingRateResult;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierRepository;
import com.locnguyen.ecommerce.domains.carrier.service.CarrierCheckoutService;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CarrierCheckoutServiceImpl implements CarrierCheckoutService {

    private final CarrierRepository carrierRepository;
    private final CarrierConfigRepository carrierConfigRepository;
    private final CarrierProviderRegistry carrierProviderRegistry;

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutCarrierOptionResponse> getCheckoutOptions() {
        return carrierRepository.findAllByStatus(CarrierStatus.ACTIVE).stream()
                .filter(this::isAvailableForCheckout)
                .sorted(Comparator.comparing(Carrier::getCreatedAt))
                .map(this::toCheckoutOption)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutCarrierQuoteResponse quote(UUID carrierId, Order order) {
        CarrierProviderContext context = resolveProviderContext(carrierId);
        ShippingRateResult result = context.provider().calculateRate(
                ShippingRateRequest.builder().order(order).build(),
                context.config());
        return CheckoutCarrierQuoteResponse.builder()
                .carrierId(context.carrier().getId())
                .carrierCode(context.carrier().getCode())
                .carrierName(context.carrier().getName())
                .carrierProviderType(context.carrier().getProviderType())
                .shippingFee(result.fee())
                .currency(result.currency())
                .serviceName(result.serviceName())
                .build();
    }

    private boolean isAvailableForCheckout(Carrier carrier) {
        CarrierProvider provider = carrierProviderRegistry.find(carrier.getProviderType().name()).orElse(null);
        if (provider == null) {
            return false;
        }
        if (!requiresEnabledConfig(carrier.getProviderType())) {
            return true;
        }
        CarrierConfig config = carrierConfigRepository.findByCarrierId(carrier.getId()).orElse(null);
        return config != null && config.isEnabled();
    }

    private CarrierProviderContext resolveProviderContext(UUID carrierId) {
        Carrier carrier = carrierRepository.findById(carrierId)
                .orElseThrow(() -> new AppException(ErrorCode.CARRIER_NOT_FOUND));
        if (carrier.getStatus() != CarrierStatus.ACTIVE) {
            throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                    "Carrier is inactive and unavailable for checkout");
        }
        CarrierProvider provider = carrierProviderRegistry.find(carrier.getProviderType().name())
                .orElseThrow(() -> new AppException(ErrorCode.CARRIER_PROVIDER_NOT_SUPPORTED,
                        "No provider bean registered for " + carrier.getProviderType()));
        CarrierConfig config = carrierConfigRepository.findByCarrierId(carrier.getId()).orElse(null);
        if (requiresEnabledConfig(carrier.getProviderType())
                && (config == null || !config.isEnabled())) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                    "Carrier config is missing or disabled for " + carrier.getCode());
        }
        return new CarrierProviderContext(carrier, provider, config);
    }

    private boolean requiresEnabledConfig(CarrierProviderType providerType) {
        return providerType != CarrierProviderType.MANUAL && providerType != CarrierProviderType.MOCK;
    }

    private CheckoutCarrierOptionResponse toCheckoutOption(Carrier carrier) {
        return CheckoutCarrierOptionResponse.builder()
                .id(carrier.getId())
                .code(carrier.getCode())
                .name(carrier.getName())
                .providerType(carrier.getProviderType())
                .logoUrl(carrier.getLogoUrl())
                .description(carrier.getDescription())
                .build();
    }

    private record CarrierProviderContext(
            Carrier carrier,
            CarrierProvider provider,
            CarrierConfig config
    ) {}
}
