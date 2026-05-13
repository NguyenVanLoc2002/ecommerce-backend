package com.locnguyen.ecommerce.domains.cart.service.impl;

import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated cart-creation helper that runs in its own REQUIRES_NEW transaction.
 *
 * <p>If the INSERT violates uq_carts_customer_active, the exception propagates
 * back to the caller AFTER this transaction has already been rolled back. The
 * caller's outer transaction is unaffected and can catch the exception, inspect
 * it, and continue (e.g. reload the existing cart).
 *
 * <p>The catch must NOT live inside this method: catching a
 * {@code DataIntegrityViolationException} inside a {@code REQUIRES_NEW}
 * transaction and then returning normally causes Spring to attempt a commit on a
 * session that is already marked rollback-only, resulting in
 * {@code UnexpectedRollbackException} (500).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartCreatorService {

    private final CartRepository cartRepository;

    /**
     * INSERT a new ACTIVE cart. Throws {@code DataIntegrityViolationException} if
     * the customer already has a cart (duplicate key on uq_carts_customer_active).
     * The caller is responsible for catching and handling that case.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cart createCart(Customer customer) {
        Cart cart = new Cart();
        cart.setCustomer(customer);
        cart = cartRepository.saveAndFlush(cart);
        log.info("Cart created: id={} customerId={}", cart.getId(), customer.getId());
        return cart;
    }
}
