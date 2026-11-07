package com.locnguyen.ecommerce.domains.inventory.entity;

import com.locnguyen.ecommerce.common.auditing.SoftDeleteEntity;
import com.locnguyen.ecommerce.domains.inventory.enums.WarehouseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Physical or logical warehouse for storing inventory.
 *
 * <p>Soft-deletable. Warehouses can be deactivated (INACTIVE) without deleting.
 */
@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Warehouse extends SoftDeleteEntity {

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "code", length = 50, nullable = false, unique = true)
    private String code;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "pickup_address", length = 500)
    private String pickupAddress;

    @Column(name = "pickup_short_address", length = 255)
    private String pickupShortAddress;

    @Column(name = "pickup_contact_name", length = 200)
    private String pickupContactName;

    @Column(name = "pickup_contact_phone", length = 50)
    private String pickupContactPhone;

    @Column(name = "pickup_lat", precision = 10, scale = 7)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", precision = 10, scale = 7)
    private BigDecimal pickupLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private WarehouseStatus status = WarehouseStatus.ACTIVE;
}
