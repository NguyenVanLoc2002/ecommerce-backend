package com.locnguyen.ecommerce.domains.cart.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.cart.dto.AddCartItemRequest;
import com.locnguyen.ecommerce.domains.cart.dto.CartResponse;
import com.locnguyen.ecommerce.domains.cart.dto.UpdateCartItemRequest;
import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.entity.CartItem;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import com.locnguyen.ecommerce.domains.cart.repository.CartItemRepository;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryRepository;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.productvariant.enums.ProductVariantStatus;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import com.locnguyen.ecommerce.domains.cart.service.impl.CartCreatorService;
import com.locnguyen.ecommerce.domains.cart.service.impl.CartServiceImpl;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CartService}.
 *
 * Tests cover:
 * - getOrCreateCart: creates cart, reuses existing, race condition, post-checkout reactivation
 * - getMyCart: returns empty response when no active cart exists
 * - addItem: variant not found, inactive variant, quantity > available, increments existing item
 * - updateItemQuantity: ownership, quantity validation
 * - removeItem: ownership enforcement
 * - clearCart: no active cart throws
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static UUID uuid(long n) { return new UUID(0L, n); }

    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductVariantRepository productVariantRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock CartCreatorService cartCreatorService;

    @InjectMocks CartServiceImpl cartService;

    // ─── factories ───────────────────────────────────────────────────────────

    private Customer customer(UUID id) {
        Customer c = mock(Customer.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    private Cart cart(UUID id, Customer customer) {
        Cart c = new Cart();
        setId(c, id);
        c.setCustomer(customer);
        c.setStatus(CartStatus.ACTIVE);
        c.setItems(new ArrayList<>());
        return c;
    }

    private Cart checkedOutCart(UUID id, Customer customer) {
        Cart c = new Cart();
        setId(c, id);
        c.setCustomer(customer);
        c.setStatus(CartStatus.CHECKED_OUT);
        c.setItems(new ArrayList<>());
        return c;
    }

    private ProductVariant activeVariant(UUID id, BigDecimal basePrice, BigDecimal salePrice) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "name", "Test Product");
        ReflectionTestUtils.setField(product, "slug", "test-product");

        ProductVariant v = new ProductVariant();
        setId(v, id);
        v.setProduct(product);
        v.setVariantName("White / M");
        v.setSku("SKU-" + id);
        v.setBasePrice(basePrice);
        v.setSalePrice(salePrice);
        v.setStatus(ProductVariantStatus.ACTIVE);
        return v;
    }

    private CartItem cartItem(UUID id, Cart cart, ProductVariant variant, int quantity) {
        CartItem ci = new CartItem();
        setId(ci, id);
        ci.setCart(cart);
        ci.setVariant(variant);
        ci.setQuantity(quantity);
        return ci;
    }

    private AddCartItemRequest addRequest(UUID variantId, int quantity) {
        AddCartItemRequest req = new AddCartItemRequest();
        req.setVariantId(variantId);
        req.setQuantity(quantity);
        return req;
    }

    private UpdateCartItemRequest updateRequest(int quantity) {
        UpdateCartItemRequest req = new UpdateCartItemRequest();
        req.setQuantity(quantity);
        return req;
    }

    private static DataIntegrityViolationException cartDuplicateKeyEx() {
        SQLException sqlEx = new SQLException(
                "Duplicate entry 'xxx' for key 'uq_carts_customer_active'");
        return new DataIntegrityViolationException("constraint", sqlEx);
    }

    private static DataIntegrityViolationException otherConstraintEx() {
        SQLException sqlEx = new SQLException(
                "Cannot add or update a child row: a foreign key constraint fails");
        return new DataIntegrityViolationException("constraint", sqlEx);
    }

    private static void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    // ─── getOrCreateCart ─────────────────────────────────────────────────────

    @Nested
    class GetOrCreateCart {

        @Test
        void returns_existing_active_cart() {
            Customer cust = customer(uuid(1));
            Cart existing = cart(uuid(5), cust);
            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(existing));

            Cart result = cartService.getOrCreateCart(cust);

            assertThat(result.getId()).isEqualTo(uuid(5));
            verify(cartCreatorService, never()).createCart(any());
        }

        @Test
        void creates_new_cart_when_none_exists() {
            Customer cust = customer(uuid(1));
            Cart newCart = cart(uuid(99), cust);
            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(cartCreatorService.createCart(cust)).thenReturn(newCart);

            Cart result = cartService.getOrCreateCart(cust);

            assertThat(result.getId()).isEqualTo(uuid(99));
            verify(cartCreatorService).createCart(cust);
        }

        @Test
        void race_condition_duplicate_key_reloads_active_cart() {
            Customer cust = customer(uuid(1));
            Cart raceWinnerCart = cart(uuid(5), cust);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(cartCreatorService.createCart(cust)).thenThrow(cartDuplicateKeyEx());
            // reload after race: finds the cart created by the concurrent request
            when(cartRepository.findByCustomerId(uuid(1))).thenReturn(Optional.of(raceWinnerCart));

            Cart result = cartService.getOrCreateCart(cust);

            assertThat(result.getId()).isEqualTo(uuid(5));
        }

        @Test
        void post_checkout_reactivates_checked_out_cart_and_clears_items() {
            Customer cust = customer(uuid(1));
            Cart checkedOut = checkedOutCart(uuid(5), cust);
            // Add a stale item from the previous order to verify it is cleared
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            checkedOut.getItems().add(cartItem(uuid(100), checkedOut, v, 2));

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(cartCreatorService.createCart(cust)).thenThrow(cartDuplicateKeyEx());
            when(cartRepository.findByCustomerId(uuid(1))).thenReturn(Optional.of(checkedOut));
            when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Cart result = cartService.getOrCreateCart(cust);

            assertThat(result.getStatus()).isEqualTo(CartStatus.ACTIVE);
            assertThat(result.getItems()).isEmpty();
            verify(cartRepository).save(checkedOut);
        }

        @Test
        void non_cart_duplicate_key_is_rethrown() {
            Customer cust = customer(uuid(1));
            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(cartCreatorService.createCart(cust)).thenThrow(otherConstraintEx());

            assertThatThrownBy(() -> cartService.getOrCreateCart(cust))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void race_condition_reload_still_empty_throws_CART_NOT_FOUND() {
            Customer cust = customer(uuid(1));
            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(cartCreatorService.createCart(cust)).thenThrow(cartDuplicateKeyEx());
            when(cartRepository.findByCustomerId(uuid(1))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.getOrCreateCart(cust))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_NOT_FOUND);
        }
    }

    // ─── getMyCart ───────────────────────────────────────────────────────────

    @Nested
    class GetMyCart {

        @Test
        void returns_empty_response_when_no_active_cart() {
            Customer cust = customer(uuid(1));
            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            CartResponse resp = cartService.getMyCart(cust);

            assertThat(resp.getTotalItems()).isZero();
            assertThat(resp.getSubTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resp.getItems()).isEmpty();
            verify(cartCreatorService, never()).createCart(any());
        }
    }

    // ─── addItem ─────────────────────────────────────────────────────────────

    @Nested
    class AddItem {

        @Test
        void throws_PRODUCT_VARIANT_NOT_FOUND_when_variant_missing() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findByIdAndDeletedFalse(uuid(99))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItem(cust, addRequest(uuid(99), 1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_VARIANT_NOT_FOUND);
        }

        @Test
        void throws_PRODUCT_VARIANT_INACTIVE_when_variant_is_inactive() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            v.setStatus(ProductVariantStatus.INACTIVE);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> cartService.addItem(cust, addRequest(uuid(1), 1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_VARIANT_INACTIVE);
        }

        @Test
        void throws_INVENTORY_NOT_ENOUGH_when_requested_exceeds_available() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(v));
            // validateQuantity throws before cartItemRepository is ever consulted
            when(inventoryRepository.sumAvailableByVariantId(uuid(1))).thenReturn(3); // only 3 available

            assertThatThrownBy(() -> cartService.addItem(cust, addRequest(uuid(1), 5))) // requesting 5
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_ENOUGH);
        }

        @Test
        void throws_CART_ITEM_QUANTITY_INVALID_when_quantity_is_zero() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> cartService.addItem(cust, addRequest(uuid(1), 0)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_ITEM_QUANTITY_INVALID);
        }

        @Test
        void creates_new_item_when_variant_not_yet_in_cart() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(v));
            when(inventoryRepository.sumAvailableByVariantId(uuid(1))).thenReturn(20);
            when(cartItemRepository.findByCartIdAndVariantId(uuid(5), uuid(1))).thenReturn(Optional.empty());
            when(cartItemRepository.save(any())).thenAnswer(inv -> {
                CartItem ci = inv.getArgument(0);
                setId(ci, uuid(100));
                return ci;
            });
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5))).thenReturn(List.of());

            cartService.addItem(cust, addRequest(uuid(1), 3));

            verify(cartItemRepository).save(argThat(ci ->
                    ci.getQuantity() == 3
                    && uuid(1).equals(ci.getVariant().getId())));
        }

        @Test
        void increments_quantity_when_variant_already_in_cart() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            CartItem existingItem = cartItem(uuid(100), c, v, 2); // already has qty=2

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(v));
            when(inventoryRepository.sumAvailableByVariantId(uuid(1))).thenReturn(10);
            when(cartItemRepository.findByCartIdAndVariantId(uuid(5), uuid(1)))
                    .thenReturn(Optional.of(existingItem));
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5)))
                    .thenReturn(List.of(existingItem));

            cartService.addItem(cust, addRequest(uuid(1), 3)); // add 3 more

            verify(cartItemRepository).save(argThat(ci -> ci.getQuantity() == 5)); // 2 + 3 = 5
        }

        @Test
        void throws_INVENTORY_NOT_ENOUGH_when_incremented_total_exceeds_available() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            CartItem existingItem = cartItem(uuid(100), c, v, 8); // already has qty=8

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(v));
            // First validateQuantity(3, uuid(1)) passes, second validateQuantity(8+3=11, uuid(1)) fails
            when(inventoryRepository.sumAvailableByVariantId(uuid(1))).thenReturn(10); // only 10 available
            when(cartItemRepository.findByCartIdAndVariantId(uuid(5), uuid(1)))
                    .thenReturn(Optional.of(existingItem));

            assertThatThrownBy(() -> cartService.addItem(cust, addRequest(uuid(1), 3))) // 8+3=11 > 10
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_ENOUGH);
        }
    }

    // ─── updateItemQuantity ───────────────────────────────────────────────────

    @Nested
    class UpdateItemQuantity {

        @Test
        void throws_CART_NOT_FOUND_when_customer_has_no_active_cart() {
            Customer cust = customer(uuid(1));
            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.updateItemQuantity(cust, uuid(100), updateRequest(2)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_NOT_FOUND);
        }

        @Test
        void throws_CART_ITEM_NOT_FOUND_when_item_does_not_exist() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findById(uuid(100))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.updateItemQuantity(cust, uuid(100), updateRequest(2)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        @Test
        void throws_CART_ITEM_NOT_FOUND_when_item_belongs_to_different_cart() {
            Customer cust = customer(uuid(1));
            Cart myCart = cart(uuid(5), cust);
            // Build a foreign cart with a different id — no Customer mock needed
            Cart otherCart = new Cart();
            setId(otherCart, uuid(99));
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            CartItem foreignItem = cartItem(uuid(100), otherCart, v, 1);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(myCart));
            when(cartItemRepository.findById(uuid(100))).thenReturn(Optional.of(foreignItem));

            assertThatThrownBy(() -> cartService.updateItemQuantity(cust, uuid(100), updateRequest(2)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        @Test
        void throws_INVENTORY_NOT_ENOUGH_when_new_quantity_exceeds_stock() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            CartItem item = cartItem(uuid(100), c, v, 2);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findById(uuid(100))).thenReturn(Optional.of(item));
            when(inventoryRepository.sumAvailableByVariantId(uuid(1))).thenReturn(3);

            assertThatThrownBy(() -> cartService.updateItemQuantity(cust, uuid(100), updateRequest(5))) // 5 > 3
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_ENOUGH);
        }

        @Test
        void updates_item_quantity_when_within_available_stock() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            CartItem item = cartItem(uuid(100), c, v, 2);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findById(uuid(100))).thenReturn(Optional.of(item));
            when(inventoryRepository.sumAvailableByVariantId(uuid(1))).thenReturn(10);
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5))).thenReturn(List.of(item));

            cartService.updateItemQuantity(cust, uuid(100), updateRequest(7));

            assertThat(item.getQuantity()).isEqualTo(7);
        }
    }

    // ─── removeItem ──────────────────────────────────────────────────────────

    @Nested
    class RemoveItem {

        @Test
        void throws_CART_NOT_FOUND_when_no_active_cart() {
            Customer cust = customer(uuid(1));
            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.removeItem(cust, uuid(100)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_NOT_FOUND);
        }

        @Test
        void throws_CART_ITEM_NOT_FOUND_when_item_belongs_to_another_cart() {
            Customer cust = customer(uuid(1));
            Cart myCart = cart(uuid(5), cust);
            Cart otherCart = new Cart();
            setId(otherCart, uuid(99));
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            CartItem foreignItem = cartItem(uuid(100), otherCart, v, 1);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(myCart));
            when(cartItemRepository.findById(uuid(100))).thenReturn(Optional.of(foreignItem));

            assertThatThrownBy(() -> cartService.removeItem(cust, uuid(100)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        @Test
        void removes_item_when_ownership_is_valid() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            CartItem item = cartItem(uuid(100), c, v, 2);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findById(uuid(100))).thenReturn(Optional.of(item));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5))).thenReturn(List.of());

            cartService.removeItem(cust, uuid(100));

            verify(cartItemRepository).delete(item);
        }
    }

    // ─── clearCart ───────────────────────────────────────────────────────────

    @Nested
    class ClearCart {

        @Test
        void throws_CART_NOT_FOUND_when_no_active_cart() {
            Customer cust = customer(uuid(1));
            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.clearCart(cust))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_NOT_FOUND);
        }

        @Test
        void clears_all_items_from_the_cart() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            c.getItems().add(cartItem(uuid(100), c, v, 2));
            c.getItems().add(cartItem(uuid(101), c, v, 1));

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            cartService.clearCart(cust);

            verify(cartRepository).save(argThat(cart -> cart.getItems().isEmpty()));
        }
    }

    // ─── subTotal calculation ─────────────────────────────────────────────────

    @Nested
    class SubTotalCalculation {

        @Test
        void getMyCart_uses_salePrice_when_set_in_lineTotal() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            // basePrice=200,000 salePrice=150,000 qty=2 → lineTotal=300,000
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("200000"), new BigDecimal("150000"));
            CartItem item = cartItem(uuid(100), c, v, 2);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5))).thenReturn(List.of(item));
            when(inventoryRepository.sumAvailableByVariantId(uuid(1))).thenReturn(10);

            CartResponse resp = cartService.getMyCart(cust);

            // subTotal should be 2 × 150,000 = 300,000
            assertThat(resp.getSubTotal()).isEqualByComparingTo("300000");
        }

        @Test
        void getMyCart_uses_basePrice_when_salePrice_is_null() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v = activeVariant(uuid(1), new BigDecimal("100000"), null);
            CartItem item = cartItem(uuid(100), c, v, 3);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5))).thenReturn(List.of(item));
            when(inventoryRepository.sumAvailableByVariantId(uuid(1))).thenReturn(10);

            CartResponse resp = cartService.getMyCart(cust);

            assertThat(resp.getSubTotal()).isEqualByComparingTo("300000");
        }

        @Test
        void totalItems_is_sum_of_all_item_quantities() {
            Customer cust = customer(uuid(1));
            Cart c = cart(uuid(5), cust);
            ProductVariant v1 = activeVariant(uuid(1), new BigDecimal("100000"), null);
            ProductVariant v2 = activeVariant(uuid(2), new BigDecimal("50000"), null);
            CartItem item1 = cartItem(uuid(100), c, v1, 3);
            CartItem item2 = cartItem(uuid(101), c, v2, 2);

            when(cartRepository.findByCustomerIdAndStatus(uuid(1), CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(uuid(5)))
                    .thenReturn(List.of(item1, item2));
            when(inventoryRepository.sumAvailableByVariantId(any(UUID.class))).thenReturn(10);

            CartResponse resp = cartService.getMyCart(cust);

            assertThat(resp.getTotalItems()).isEqualTo(5); // 3 + 2
        }
    }
}
