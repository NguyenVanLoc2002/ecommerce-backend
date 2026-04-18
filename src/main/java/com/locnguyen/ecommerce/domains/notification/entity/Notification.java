package com.locnguyen.ecommerce.domains.notification.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * In-app notification for a customer.
 *
 * <p>Notifications are permanent records (no soft delete).
 * {@code referenceId} + {@code referenceType} optionally link to a related
 * domain entity so the front-end can deep-link (e.g., to an order detail page).
 *
 * <p>Extends {@link BaseEntity} — no soft delete.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private NotificationType type;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    /** PK of the related domain entity, if applicable (e.g., orderId, reviewId). */
    @Column(name = "reference_id")
    private Long referenceId;

    /** Domain type of the related entity, e.g. "ORDER", "REVIEW". */
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
