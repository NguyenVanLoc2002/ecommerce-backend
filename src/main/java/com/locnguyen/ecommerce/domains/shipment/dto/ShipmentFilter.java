package com.locnguyen.ecommerce.domains.shipment.dto;

import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ShipmentFilter {
    private UUID orderId;
    private String orderCode;
    private UUID carrierId;
    private String carrier;
    private ShipmentStatus status;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
