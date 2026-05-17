package com.locnguyen.ecommerce.domains.carrier.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ToggleCarrierRequest {

    @NotNull
    private Boolean active;
}
