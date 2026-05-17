package com.locnguyen.ecommerce.domains.carrier.dto;

import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class CheckoutCarrierQuoteResponse {
    private final UUID carrierId;
    private final String carrierCode;
    private final String carrierName;
    private final CarrierProviderType carrierProviderType;
    private final BigDecimal shippingFee;
    private final String currency;
    private final String serviceName;
}
