package com.locnguyen.ecommerce.domains.carrier.dto;

import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CarrierFilter {
    private String keyword;
    private CarrierProviderType providerType;
    private CarrierStatus status;
    private Boolean enabled;
}
