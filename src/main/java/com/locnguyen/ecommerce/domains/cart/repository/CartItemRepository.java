package com.locnguyen.ecommerce.domains.cart.repository;

import com.locnguyen.ecommerce.domains.cart.entity.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import java.util.UUID;
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByCartId(UUID cartId);

    @EntityGraph(attributePaths = {"variant", "variant.product", "variant.product.brand", "variant.product.media"})
    List<CartItem> findByCartIdOrderByCreatedAtAsc(UUID cartId);

    Optional<CartItem> findByCartIdAndVariantId(UUID cartId, UUID variantId);

    boolean existsByCartIdAndVariantId(UUID cartId, UUID variantId);
}
