package com.locnguyen.ecommerce.domains.shipment.dto;

import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "Request to advance shipment status and record a tracking event")
public class UpdateShipmentStatusRequest {

    @NotNull
    @Schema(description = "New status. Must be a valid forward transition.")
    private ShipmentStatus status;

    @Size(max = 255)
    @Schema(description = "Physical location of the event, e.g. 'Ho Chi Minh City hub'")
    private String location;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "Description shown on the tracking timeline")
    private String description;

    @Schema(description = "Time the event occurred. Defaults to now if omitted.")
    private LocalDateTime eventTime;
}
