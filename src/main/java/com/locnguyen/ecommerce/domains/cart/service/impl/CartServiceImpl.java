package com.locnguyen.ecommerce.domains.cart.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.cart.dto.*;
import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.entity.CartItem;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.cart.repository.CartItemRepository;
import com.locnguyen.ecommerce.domains.cart.service.CartService;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryRepository;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.productvariant.enums.ProductVariantStatus;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final CartCreatorService cartCreatorService;

    // ─── Cart retrieval ──────────────────────────────────────────────────────

    /**
     * Return the current customer's cart summary. Does NOT create or reactivate
     * a cart — a GET should never mutate state.
     */
    @Transactional(readOnly = true)
    public CartResponse getMyCart(Customer customer) {
        return cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .map(this::buildCartResponse)
                .orElseGet(() -> CartResponse.builder()
                        .items(List.of())
                        .totalItems(0)
                        .subTotal(BigDecimal.ZERO)
                        .build());
    }

    /**
     * Find the customer's ACTIVE cart or create one.
     *
     * <p>Three cases handled:
     * <ol>
     *   <li>ACTIVE cart already exists → return it immediately.</li>
     *   <li>No cart at all → {@link CartCreatorService#createCart} succeeds → return new cart.</li>
     *   <li>Duplicate-key on uq_carts_customer_active:
     *     <ul>
     *       <li>Race condition (concurrent insert won) → reload the ACTIVE cart.</li>
     *       <li>Post-checkout (CHECKED_OUT cart exists) → reset it to ACTIVE,
     *           clear stale items, and return.</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p>The catch lives HERE (outside the {@code REQUIRES_NEW} transaction in
     * {@link CartCreatorService}). Catching a {@code DataIntegrityViolationException}
     * inside a {@code REQUIRES_NEW} transaction and returning normally forces Spring
     * to commit a session already marked rollback-only, causing
     * {@code UnexpectedRollbackException}. By letting the exception propagate out of
     * the inner transaction first, Spring rolls back the inner session cleanly; the
     * outer transaction's session is unaffected and safe to continue.
     */
    @Transactional
    public Cart getOrCreateCart(Customer customer) {
        Optional<Cart> existing = cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return cartCreatorService.createCart(customer);
        } catch (DataIntegrityViolationException ex) {
            if (!isCartDuplicateKey(ex)) {
                throw ex;
            }
            log.debug("Duplicate cart key for customerId={}, reloading existing cart", customer.getId());
            return cartRepository.findByCustomerId(customer.getId())
                    .map(this::reactivateIfCheckedOut)
                    .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));
        }
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

    /**
     * If the cart is CHECKED_OUT (post-checkout), reset it to ACTIVE and clear
     * stale items so the customer starts a fresh shopping session.
     */
    private Cart reactivateIfCheckedOut(Cart cart) {
        if (cart.getStatus() == CartStatus.CHECKED_OUT) {
            log.info("Reactivating CHECKED_OUT cart id={} customerId={}",
                    cart.getId(), cart.getCustomer().getId());
            cart.getItems().clear();
            cart.setStatus(CartStatus.ACTIVE);
            return cartRepository.save(cart);
        }
        return cart;
    }

    private boolean isCartDuplicateKey(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        return msg != null && msg.contains("uq_carts_customer_active");
    }

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
