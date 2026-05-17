package com.locnguyen.ecommerce.domains.carrier.dto;

import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CheckoutCarrierOptionResponse {
    private final UUID id;
    private final String code;
    private final String name;
    private final CarrierProviderType providerType;
    private final String logoUrl;
    private final String description;
}
