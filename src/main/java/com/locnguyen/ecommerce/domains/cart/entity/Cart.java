package com.locnguyen.ecommerce.domains.cart.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Shopping cart — one active cart per customer.
 *
 * <p>Extends {@link BaseEntity} (no soft delete) — cart records are lightweight
 * and managed via status (ACTIVE / CHECKED_OUT).
 *
 * <p>Unique constraint on customer_id ensures only one active cart exists per customer.
 */
@Entity
@Table(name = "carts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_carts_customer_active",
                columnNames = {"customer_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Cart extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    @ToString.Exclude
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private CartStatus status = CartStatus.ACTIVE;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<CartItem> items = new ArrayList<>();
}
