package com.locnguyen.ecommerce.domains.shipment.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Immutable tracking event for a shipment.
 *
 * <p>Every status change and carrier scan is recorded here.
 * Records are never updated or deleted — they form the tracking timeline
 * visible to the customer.
 */
@Entity
@Table(name = "shipment_events")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private ShipmentStatus status;

    /** Human-readable location, e.g. "Ho Chi Minh City hub". */
    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "description", length = 500, nullable = false)
    private String description;

    /** The time this event actually occurred (may differ from {@code createdAt}). */
    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;
}
