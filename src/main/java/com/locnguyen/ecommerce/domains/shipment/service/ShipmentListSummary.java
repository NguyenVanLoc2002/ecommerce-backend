package com.locnguyen.ecommerce.domains.shipment.service;

import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;

import java.util.UUID;

public record ShipmentListSummary(UUID orderId, ShipmentStatus status, String carrier,
                                  String trackingNumber) {
}
