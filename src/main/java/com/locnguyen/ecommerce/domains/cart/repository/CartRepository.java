package com.locnguyen.ecommerce.domains.cart.repository;

import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByCustomerIdAndStatus(UUID customerId, CartStatus status);

    Optional<Cart> findByCustomerId(UUID customerId);

    /**
     * Load the ACTIVE cart with a PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) lock.
     *
     * <p>Used exclusively by checkout (createOrder) to prevent two concurrent
     * requests from both reading the same ACTIVE cart and each creating an order.
     * The first request acquires the lock and proceeds; the second waits. Once
     * the first transaction commits (cart moves to CHECKED_OUT), the second
     * transaction reloads the row, finds no ACTIVE cart, and fails safely with
     * CART_NOT_FOUND — no duplicate order is created.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.customer.id = :customerId AND c.status = :status")
    Optional<Cart> findByCustomerIdAndStatusWithLock(
            @Param("customerId") UUID customerId,
            @Param("status") CartStatus status);
}
