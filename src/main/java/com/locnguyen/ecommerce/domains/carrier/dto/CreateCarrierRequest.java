package com.locnguyen.ecommerce.domains.carrier.dto;

import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCarrierRequest {

    @NotBlank
    @Size(max = 50)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotNull
    private CarrierProviderType providerType;

    private CarrierStatus status;

    @Size(max = 500)
    private String logoUrl;

    @Size(max = 500)
    private String description;
}
