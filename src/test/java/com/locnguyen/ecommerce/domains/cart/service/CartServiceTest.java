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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CartService}.
 *
 * Tests cover:
 * - getOrCreateCart: creates cart when absent, returns existing
 * - addItem: variant not found, inactive variant, quantity > available, increments existing item
 * - updateItemQuantity: ownership, quantity validation
 * - removeItem: ownership enforcement
 * - clearCart: no active cart throws
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductVariantRepository productVariantRepository;
    @Mock InventoryRepository inventoryRepository;

    @InjectMocks CartService cartService;

    // ─── factories ───────────────────────────────────────────────────────────

    private Customer customer(long id) {
        Customer c = mock(Customer.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    private Cart cart(long id, Customer customer) {
        Cart c = new Cart();
        setId(c, id);
        c.setCustomer(customer);
        c.setStatus(CartStatus.ACTIVE);
        c.setItems(new ArrayList<>());
        return c;
    }

    private ProductVariant activeVariant(long id, BigDecimal basePrice, BigDecimal salePrice) {
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

    private CartItem cartItem(long id, Cart cart, ProductVariant variant, int quantity) {
        CartItem ci = new CartItem();
        setId(ci, id);
        ci.setCart(cart);
        ci.setVariant(variant);
        ci.setQuantity(quantity);
        return ci;
    }

    private AddCartItemRequest addRequest(long variantId, int quantity) {
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

    private static void setId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    // ─── getOrCreateCart ─────────────────────────────────────────────────────

    @Nested
    class GetOrCreateCart {

        @Test
        void returns_existing_active_cart() {
            Customer cust = customer(1L);
            Cart existing = cart(5L, cust);
            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(existing));

            Cart result = cartService.getOrCreateCart(cust);

            assertThat(result.getId()).isEqualTo(5L);
            verify(cartRepository, never()).save(any());
        }

        @Test
        void creates_new_cart_when_none_exists() {
            Customer cust = customer(1L);
            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(cartRepository.save(any())).thenAnswer(inv -> {
                Cart c = inv.getArgument(0);
                setId(c, 99L);
                return c;
            });

            Cart result = cartService.getOrCreateCart(cust);

            assertThat(result).isNotNull();
            verify(cartRepository).save(any(Cart.class));
        }
    }

    // ─── addItem ─────────────────────────────────────────────────────────────

    @Nested
    class AddItem {

        @Test
        void throws_PRODUCT_VARIANT_NOT_FOUND_when_variant_missing() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItem(cust, addRequest(99L, 1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_VARIANT_NOT_FOUND);
        }

        @Test
        void throws_PRODUCT_VARIANT_INACTIVE_when_variant_is_inactive() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);
            v.setStatus(ProductVariantStatus.INACTIVE);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findById(1L)).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> cartService.addItem(cust, addRequest(1L, 1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_VARIANT_INACTIVE);
        }

        @Test
        void throws_INVENTORY_NOT_ENOUGH_when_requested_exceeds_available() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findById(1L)).thenReturn(Optional.of(v));
            // validateQuantity throws before cartItemRepository is ever consulted
            when(inventoryRepository.sumAvailableByVariantId(1L)).thenReturn(3); // only 3 available

            assertThatThrownBy(() -> cartService.addItem(cust, addRequest(1L, 5))) // requesting 5
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_ENOUGH);
        }

        @Test
        void throws_CART_ITEM_QUANTITY_INVALID_when_quantity_is_zero() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findById(1L)).thenReturn(Optional.of(v));

            assertThatThrownBy(() -> cartService.addItem(cust, addRequest(1L, 0)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_ITEM_QUANTITY_INVALID);
        }

        @Test
        void creates_new_item_when_variant_not_yet_in_cart() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findById(1L)).thenReturn(Optional.of(v));
            when(inventoryRepository.sumAvailableByVariantId(1L)).thenReturn(20);
            when(cartItemRepository.findByCartIdAndVariantId(5L, 1L)).thenReturn(Optional.empty());
            when(cartItemRepository.save(any())).thenAnswer(inv -> {
                CartItem ci = inv.getArgument(0);
                setId(ci, 100L);
                return ci;
            });
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L)).thenReturn(List.of());

            cartService.addItem(cust, addRequest(1L, 3));

            verify(cartItemRepository).save(argThat(ci ->
                    ci.getQuantity() == 3
                    && ci.getVariant().getId() == 1L));
        }

        @Test
        void increments_quantity_when_variant_already_in_cart() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);
            CartItem existingItem = cartItem(100L, c, v, 2); // already has qty=2

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findById(1L)).thenReturn(Optional.of(v));
            when(inventoryRepository.sumAvailableByVariantId(1L)).thenReturn(10);
            when(cartItemRepository.findByCartIdAndVariantId(5L, 1L))
                    .thenReturn(Optional.of(existingItem));
            when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L))
                    .thenReturn(List.of(existingItem));

            cartService.addItem(cust, addRequest(1L, 3)); // add 3 more

            verify(cartItemRepository).save(argThat(ci -> ci.getQuantity() == 5)); // 2 + 3 = 5
        }

        @Test
        void throws_INVENTORY_NOT_ENOUGH_when_incremented_total_exceeds_available() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);
            CartItem existingItem = cartItem(100L, c, v, 8); // already has qty=8

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(productVariantRepository.findById(1L)).thenReturn(Optional.of(v));
            // First validateQuantity(3, 1L) passes, second validateQuantity(8+3=11, 1L) fails
            when(inventoryRepository.sumAvailableByVariantId(1L)).thenReturn(10); // only 10 available
            when(cartItemRepository.findByCartIdAndVariantId(5L, 1L))
                    .thenReturn(Optional.of(existingItem));

            assertThatThrownBy(() -> cartService.addItem(cust, addRequest(1L, 3))) // 8+3=11 > 10
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
            Customer cust = customer(1L);
            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.updateItemQuantity(cust, 100L, updateRequest(2)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_NOT_FOUND);
        }

        @Test
        void throws_CART_ITEM_NOT_FOUND_when_item_does_not_exist() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.updateItemQuantity(cust, 100L, updateRequest(2)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        @Test
        void throws_CART_ITEM_NOT_FOUND_when_item_belongs_to_different_cart() {
            Customer cust = customer(1L);
            Cart myCart = cart(5L, cust);
            // Build a foreign cart with a different id — no Customer mock needed
            Cart otherCart = new Cart();
            setId(otherCart, 99L);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);
            CartItem foreignItem = cartItem(100L, otherCart, v, 1);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(myCart));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(foreignItem));

            assertThatThrownBy(() -> cartService.updateItemQuantity(cust, 100L, updateRequest(2)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        @Test
        void throws_INVENTORY_NOT_ENOUGH_when_new_quantity_exceeds_stock() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);
            CartItem item = cartItem(100L, c, v, 2);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));
            when(inventoryRepository.sumAvailableByVariantId(1L)).thenReturn(3);

            assertThatThrownBy(() -> cartService.updateItemQuantity(cust, 100L, updateRequest(5))) // 5 > 3
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_ENOUGH);
        }

        @Test
        void updates_item_quantity_when_within_available_stock() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);
            CartItem item = cartItem(100L, c, v, 2);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));
            when(inventoryRepository.sumAvailableByVariantId(1L)).thenReturn(10);
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(item));

            cartService.updateItemQuantity(cust, 100L, updateRequest(7));

            assertThat(item.getQuantity()).isEqualTo(7);
        }
    }

    // ─── removeItem ──────────────────────────────────────────────────────────

    @Nested
    class RemoveItem {

        @Test
        void throws_CART_NOT_FOUND_when_no_active_cart() {
            Customer cust = customer(1L);
            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.removeItem(cust, 100L))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_NOT_FOUND);
        }

        @Test
        void throws_CART_ITEM_NOT_FOUND_when_item_belongs_to_another_cart() {
            Customer cust = customer(1L);
            Cart myCart = cart(5L, cust);
            Cart otherCart = new Cart();
            setId(otherCart, 99L);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);
            CartItem foreignItem = cartItem(100L, otherCart, v, 1);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(myCart));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(foreignItem));

            assertThatThrownBy(() -> cartService.removeItem(cust, 100L))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        @Test
        void removes_item_when_ownership_is_valid() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);
            CartItem item = cartItem(100L, c, v, 2);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findById(100L)).thenReturn(Optional.of(item));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L)).thenReturn(List.of());

            cartService.removeItem(cust, 100L);

            verify(cartItemRepository).delete(item);
        }
    }

    // ─── clearCart ───────────────────────────────────────────────────────────

    @Nested
    class ClearCart {

        @Test
        void throws_CART_NOT_FOUND_when_no_active_cart() {
            Customer cust = customer(1L);
            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.clearCart(cust))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CART_NOT_FOUND);
        }

        @Test
        void clears_all_items_from_the_cart() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);
            c.getItems().add(cartItem(100L, c, v, 2));
            c.getItems().add(cartItem(101L, c, v, 1));

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
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
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            // basePrice=200,000 salePrice=150,000 qty=2 → lineTotal=300,000
            ProductVariant v = activeVariant(1L, new BigDecimal("200000"), new BigDecimal("150000"));
            CartItem item = cartItem(100L, c, v, 2);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(item));
            when(inventoryRepository.sumAvailableByVariantId(1L)).thenReturn(10);

            CartResponse resp = cartService.getMyCart(cust);

            // subTotal should be 2 × 150,000 = 300,000
            assertThat(resp.getSubTotal()).isEqualByComparingTo("300000");
        }

        @Test
        void getMyCart_uses_basePrice_when_salePrice_is_null() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v = activeVariant(1L, new BigDecimal("100000"), null);
            CartItem item = cartItem(100L, c, v, 3);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(item));
            when(inventoryRepository.sumAvailableByVariantId(1L)).thenReturn(10);

            CartResponse resp = cartService.getMyCart(cust);

            assertThat(resp.getSubTotal()).isEqualByComparingTo("300000");
        }

        @Test
        void totalItems_is_sum_of_all_item_quantities() {
            Customer cust = customer(1L);
            Cart c = cart(5L, cust);
            ProductVariant v1 = activeVariant(1L, new BigDecimal("100000"), null);
            ProductVariant v2 = activeVariant(2L, new BigDecimal("50000"), null);
            CartItem item1 = cartItem(100L, c, v1, 3);
            CartItem item2 = cartItem(101L, c, v2, 2);

            when(cartRepository.findByCustomerIdAndStatus(1L, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(c));
            when(cartItemRepository.findByCartIdOrderByCreatedAtAsc(5L))
                    .thenReturn(List.of(item1, item2));
            when(inventoryRepository.sumAvailableByVariantId(anyLong())).thenReturn(10);

            CartResponse resp = cartService.getMyCart(cust);

            assertThat(resp.getTotalItems()).isEqualTo(5); // 3 + 2
        }
    }
}
