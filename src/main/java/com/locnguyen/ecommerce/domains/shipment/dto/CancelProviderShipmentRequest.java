package com.locnguyen.ecommerce.domains.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelProviderShipmentRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
