package com.locnguyen.ecommerce.domains.shipment.dto;

import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ShipmentFilter {
    private Long orderId;
    private String orderCode;
    private String carrier;
    private ShipmentStatus status;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
