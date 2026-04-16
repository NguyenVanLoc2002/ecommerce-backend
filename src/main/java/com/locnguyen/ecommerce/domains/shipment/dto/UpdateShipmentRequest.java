package com.locnguyen.ecommerce.domains.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Request to update shipment details. Null fields are ignored.")
public class UpdateShipmentRequest {

    @Size(max = 100)
    private String carrier;

    @Size(max = 200)
    private String trackingNumber;

    private LocalDate estimatedDeliveryDate;

    @DecimalMin("0")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal shippingFee;

    @Size(max = 500)
    private String note;
}
