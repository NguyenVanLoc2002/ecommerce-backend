package com.locnguyen.ecommerce.domains.cart.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.cart.dto.*;
import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.entity.CartItem;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.cart.repository.CartItemRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryRepository;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.productvariant.enums.ProductVariantStatus;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;

    // ─── Cart retrieval ──────────────────────────────────────────────────────

    /**
     * Get or create the active cart for the current customer.
     */
    @Transactional
    public Cart getOrCreateCart(Customer customer) {
        return cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setCustomer(customer);
                    cart = cartRepository.save(cart);
                    log.info("Cart created: id={} customerId={}", cart.getId(), customer.getId());
                    return cart;
                });
    }

    @Transactional(readOnly = true)
    public CartResponse getMyCart(Customer customer) {
        Cart cart = getOrCreateCart(customer);
        return buildCartResponse(cart);
    }

    // ─── Item operations ─────────────────────────────────────────────────────

    @Transactional
    public CartResponse addItem(Customer customer, AddCartItemRequest request) {
        Cart cart = getOrCreateCart(customer);

        ProductVariant variant = findActiveVariant(request.getVariantId());
        validateQuantity(request.getQuantity(), variant.getId());

        CartItem item = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.getId())
                .orElseGet(() -> {
                    CartItem ci = new CartItem();
                    ci.setCart(cart);
                    ci.setVariant(variant);
                    return ci;
                });

        int newQuantity = item.getId() == null
                ? request.getQuantity()
                : item.getQuantity() + request.getQuantity();

        validateQuantity(newQuantity, variant.getId());
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);

        log.info("Cart item added: cartId={} variantId={} qty={} by={}",
                cart.getId(), variant.getId(), newQuantity, SecurityUtils.getCurrentUsernameOrSystem());
        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(Customer customer, UUID itemId, UpdateCartItemRequest request) {
        Cart cart = cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        validateQuantity(request.getQuantity(), item.getVariant().getId());
        item.setQuantity(request.getQuantity());

        log.info("Cart item updated: cartId={} itemId={} qty={} by={}",
                cart.getId(), itemId, request.getQuantity(), SecurityUtils.getCurrentUsernameOrSystem());
        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Customer customer, UUID itemId) {
        Cart cart = cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new AppException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        cartItemRepository.delete(item);

        log.info("Cart item removed: cartId={} itemId={} by={}",
                cart.getId(), itemId, SecurityUtils.getCurrentUsernameOrSystem());
        return buildCartResponse(cart);
    }

    @Transactional
    public void clearCart(Customer customer) {
        Cart cart = cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        cart.getItems().clear();
        cartRepository.save(cart);

        log.info("Cart cleared: cartId={} by={}", cart.getId(), SecurityUtils.getCurrentUsernameOrSystem());
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private ProductVariant findActiveVariant(UUID variantId) {
        ProductVariant variant = productVariantRepository.findByIdAndDeletedFalse(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
        if (variant.getStatus() != ProductVariantStatus.ACTIVE) {
            throw new AppException(ErrorCode.PRODUCT_VARIANT_INACTIVE);
        }
        return variant;
    }

    private void validateQuantity(int quantity, UUID variantId) {
        if (quantity <= 0) {
            throw new AppException(ErrorCode.CART_ITEM_QUANTITY_INVALID);
        }

        int available = inventoryRepository.sumAvailableByVariantId(variantId);
        if (quantity > available) {
            throw new AppException(ErrorCode.INVENTORY_NOT_ENOUGH,
                    "Requested " + quantity + " but only " + available + " available");
        }
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());

        List<CartItemResponse> itemResponses = items.stream()
                .map(this::buildItemResponse)
                .toList();

        int totalItems = itemResponses.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        BigDecimal subTotal = itemResponses.stream()
                .map(CartItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(cart.getId())
                .items(itemResponses)
                .totalItems(totalItems)
                .subTotal(subTotal)
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private CartItemResponse buildItemResponse(CartItem item) {
        ProductVariant variant = item.getVariant();
        BigDecimal effectivePrice = variant.getSalePrice() != null
                ? variant.getSalePrice()
                : variant.getBasePrice();

        int availableStock = inventoryRepository.sumAvailableByVariantId(variant.getId());

        return CartItemResponse.builder()
                .id(item.getId())
                .variantId(variant.getId())
                .variantName(variant.getVariantName())
                .sku(variant.getSku())
                .productName(variant.getProduct().getName())
                .productSlug(variant.getProduct().getSlug())
                .unitPrice(variant.getBasePrice())
                .salePrice(variant.getSalePrice())
                .quantity(item.getQuantity())
                .availableStock(availableStock)
                .lineTotal(effectivePrice.multiply(BigDecimal.valueOf(item.getQuantity())))
                .createdAt(item.getCreatedAt())
                .build();
    }
}
