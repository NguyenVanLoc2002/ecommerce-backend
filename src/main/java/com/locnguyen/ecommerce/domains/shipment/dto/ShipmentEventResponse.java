package com.locnguyen.ecommerce.domains.shipment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A single tracking event on the shipment timeline")
public class ShipmentEventResponse {

    private final Long id;
    private final String status;
    private final String location;
    private final String description;
    private final LocalDateTime eventTime;
}
