package com.locnguyen.ecommerce.domains.cart.service.impl;

import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CartCreatorService}.
 *
 * Key behaviour: createCart runs in REQUIRES_NEW and must NOT swallow
 * DataIntegrityViolationException — the caller (CartServiceImpl) inspects the
 * exception and decides whether it represents a known race condition or an
 * unexpected constraint failure.
 */
@ExtendWith(MockitoExtension.class)
class CartCreatorServiceTest {

    @Mock CartRepository cartRepository;
    @InjectMocks CartCreatorService cartCreatorService;

    private static UUID uuid(long n) { return new UUID(0L, n); }

    private Customer customer(UUID id) {
        Customer c = mock(Customer.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    @Test
    void createCart_saves_and_returns_new_cart() {
        Customer cust = customer(uuid(1));
        Cart saved = new Cart();
        ReflectionTestUtils.setField(saved, "id", uuid(99));
        saved.setCustomer(cust);

        when(cartRepository.saveAndFlush(any())).thenReturn(saved);

        Cart result = cartCreatorService.createCart(cust);

        assertThat(result.getId()).isEqualTo(uuid(99));
    }

    @Test
    void createCart_propagates_uq_carts_customer_active_violation() {
        // getId() is only called in log.info after saveAndFlush — plain mock avoids UnnecessaryStubbing
        Customer cust = mock(Customer.class);
        SQLException sqlEx = new SQLException(
                "Duplicate entry '" + uuid(1) + "' for key 'uq_carts_customer_active'");
        DataIntegrityViolationException dive = new DataIntegrityViolationException("constraint", sqlEx);

        when(cartRepository.saveAndFlush(any())).thenThrow(dive);

        // Must propagate — caller is responsible for handling the race condition
        assertThatThrownBy(() -> cartCreatorService.createCart(cust))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createCart_propagates_non_cart_data_integrity_violation() {
        // getId() is never reached in this path — plain mock avoids UnnecessaryStubbing
        Customer cust = mock(Customer.class);
        SQLException sqlEx = new SQLException("Cannot add or update a child row: a foreign key constraint fails");
        DataIntegrityViolationException dive = new DataIntegrityViolationException("constraint", sqlEx);

        when(cartRepository.saveAndFlush(any())).thenThrow(dive);

        assertThatThrownBy(() -> cartCreatorService.createCart(cust))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
