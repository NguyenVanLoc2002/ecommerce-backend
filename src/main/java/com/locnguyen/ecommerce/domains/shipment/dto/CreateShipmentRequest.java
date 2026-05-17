package com.locnguyen.ecommerce.domains.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Request to create a shipment for an order")
public class CreateShipmentRequest {

    @NotNull
    @Schema(description = "The order to ship. Must be in PROCESSING status.")
    private UUID orderId;

    @Schema(description = "Existing carrier ID. When present, the shipment can be created via the carrier provider.")
    private UUID carrierId;

    @Size(max = 100)
    @Schema(description = "Carrier name, e.g. GHTK, GHN, VNPOST, J&T. Required only for manual shipments.")
    private String carrier;

    @Size(max = 200)
    @Schema(description = "Carrier tracking reference. For provider-based shipments this can be omitted.")
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
