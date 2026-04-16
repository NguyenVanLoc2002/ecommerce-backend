package com.locnguyen.ecommerce.domains.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "Request to create a shipment for an order")
public class CreateShipmentRequest {

    @NotNull
    @Schema(description = "The order to ship. Must be in PROCESSING status.")
    private Long orderId;

    @NotBlank
    @Size(max = 100)
    @Schema(description = "Carrier name, e.g. GHTK, GHN, VNPOST, J&T")
    private String carrier;

    @Size(max = 200)
    @Schema(description = "Carrier's own tracking reference — can be set later via update")
    private String trackingNumber;

    @Schema(description = "Estimated delivery date shown to customer")
    private LocalDate estimatedDeliveryDate;

    @DecimalMin("0")
    @Digits(integer = 16, fraction = 2)
    @Schema(description = "Shipping fee charged (defaults to 0)")
    private BigDecimal shippingFee;

    @Size(max = 500)
    private String note;
}
