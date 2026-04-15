package com.locnguyen.ecommerce.domains.order.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.address.entity.Address;
import com.locnguyen.ecommerce.domains.address.repository.AddressRepository;
import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.entity.CartItem;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import com.locnguyen.ecommerce.domains.cart.repository.CartItemRepository;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.inventory.dto.ReserveStockRequest;
import com.locnguyen.ecommerce.domains.inventory.entity.Inventory;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryRepository;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import com.locnguyen.ecommerce.domains.order.dto.*;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.mapper.OrderMapper;
import com.locnguyen.ecommerce.domains.order.repository.OrderItemRepository;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final PaymentService paymentService;

    // ─── Create order from cart ─────────────────────────────────────────────

    /**
     * Create a new order from the customer's active cart.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate cart has items</li>
     *   <li>Validate shipping address ownership</li>
     *   <li>Snapshot product data from cart items</li>
     *   <li>Calculate pricing (subTotal, discount, shippingFee, totalAmount)</li>
     *   <li>Persist order + order items</li>
     *   <li>Reserve inventory for each item</li>
     *   <li>Mark cart as CHECKED_OUT</li>
     * </ol>
     *
     * <p>Everything runs in a single transaction — if any step fails,
     * the entire operation rolls back.
     */
    @Transactional
    public OrderResponse createOrder(Customer customer, CreateOrderRequest request) {
        // 1. Load active cart
        Cart cart = cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        List<CartItem> cartItems = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw new AppException(ErrorCode.ORDER_EMPTY);
        }

        // 2. Validate and snapshot shipping address
        Address address = addressRepository.findById(request.getShippingAddressId())
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        if (!address.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND);
        }

        // 3. Build order
        String orderCode = CodeGenerator.generateOrderCode();
        PaymentMethod paymentMethod = request.getPaymentMethod() != null
                ? PaymentMethod.valueOf(request.getPaymentMethod())
                : PaymentMethod.COD;

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderCode(orderCode);
        // ONLINE orders move directly to AWAITING_PAYMENT — inventory is reserved
        // and we are now waiting for the customer to complete payment.
        // COD orders start at PENDING — admin confirms on delivery.
        order.setStatus(paymentMethod == PaymentMethod.ONLINE
                ? OrderStatus.AWAITING_PAYMENT
                : OrderStatus.PENDING);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setCustomerNote(request.getCustomerNote());
        order.setVoucherCode(request.getVoucherCode());

        // Snapshot shipping address
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getPhoneNumber());
        order.setShippingStreet(address.getStreetAddress());
        order.setShippingWard(address.getWard());
        order.setShippingDistrict(address.getDistrict());
        order.setShippingCity(address.getCity());
        order.setShippingPostalCode(address.getPostalCode());

        // 4. Build order items and calculate subTotal
        BigDecimal subTotal = BigDecimal.ZERO;

        for (CartItem ci : cartItems) {
            ProductVariant variant = ci.getVariant();
            BigDecimal effectivePrice = variant.getSalePrice() != null
                    ? variant.getSalePrice()
                    : variant.getBasePrice();
            BigDecimal lineTotal = effectivePrice.multiply(BigDecimal.valueOf(ci.getQuantity()));

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setVariant(variant);
            item.setProductName(variant.getProduct().getName());
            item.setVariantName(variant.getVariantName());
            item.setSku(variant.getSku());
            item.setUnitPrice(variant.getBasePrice());
            item.setSalePrice(variant.getSalePrice());
            item.setQuantity(ci.getQuantity());
            item.setLineTotal(lineTotal);

            order.getItems().add(item);
            subTotal = subTotal.add(lineTotal);
        }

        // 5. Calculate totals
        // Voucher discount is a prepare hook — will be properly calculated
        // when the promotion module is implemented. For now, discount = 0.
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal shippingFee = BigDecimal.ZERO; // TODO: calculate based on rules

        order.setSubTotal(subTotal);
        order.setDiscountAmount(discountAmount);
        order.setShippingFee(shippingFee);
        order.setTotalAmount(subTotal.subtract(discountAmount).add(shippingFee));

        // 6. Persist order (cascades to order items)
        order = orderRepository.save(order);

        // 7. Reserve inventory for each item
        for (CartItem ci : cartItems) {
            // Find the inventory record for this variant (use first available warehouse)
            List<Inventory> inventories = inventoryRepository.findByVariantId(ci.getVariant().getId());
            if (inventories.isEmpty()) {
                throw new AppException(ErrorCode.INVENTORY_NOT_FOUND,
                        "No inventory record found for variant " + ci.getVariant().getId());
            }

            // Pick the warehouse with the most available stock
            Inventory bestInventory = inventories.stream()
                    .max((a, b) -> Integer.compare(
                            a.getOnHand() - a.getReserved(),
                            b.getOnHand() - b.getReserved()))
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_ENOUGH));

            ReserveStockRequest reserveRequest = new ReserveStockRequest();
            reserveRequest.setVariantId(ci.getVariant().getId());
            reserveRequest.setWarehouseId(bestInventory.getWarehouse().getId());
            reserveRequest.setQuantity(ci.getQuantity());
            reserveRequest.setReferenceType("ORDER");
            reserveRequest.setReferenceId(orderCode);
            // Reservation expires in 24 hours (auto-release if order not confirmed)
            reserveRequest.setExpiresAt(LocalDateTime.now().plusHours(24));

            inventoryService.reserveStock(reserveRequest);
        }

        // 8. Mark cart as checked out
        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        // 9. Create payment record
        // COD: payment record created immediately (customer pays on delivery).
        // ONLINE: payment record is created later when customer calls initiatePayment.
        if (paymentMethod == PaymentMethod.COD) {
            paymentService.createCodPayment(order);
        }

        log.info("Order created: code={} customerId={} items={} total={} payment={} by={}",
                orderCode, customer.getId(), cartItems.size(), order.getTotalAmount(),
                paymentMethod, SecurityUtils.getCurrentUsernameOrSystem());

        return buildOrderResponse(order);
    }

    // ─── Read operations ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<OrderListItemResponse> getMyOrders(Customer customer, Pageable pageable) {
        Page<Order> page = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), pageable);
        List<OrderListItemResponse> items = page.getContent().stream()
                .map(this::buildListItemResponse)
                .toList();
        return PagedResponse.of(items, page);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Customer customer) {
        Order order = findOrThrow(orderId);
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }
        return buildOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByCode(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return buildOrderResponse(order);
    }

    // ─── State machine transitions ──────────────────────────────────────────

    /**
     * Confirm order — PENDING → CONFIRMED or AWAITING_PAYMENT → CONFIRMED.
     *
     * <p>Per order-lifecycle.md:
     * <ul>
     *   <li>COD: always allowed</li>
     *   <li>Online: requires payment_status = PAID</li>
     * </ul>
     */
    @Transactional
    public OrderResponse confirmOrder(Long orderId) {
        Order order = findOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        // Allow confirmation from either PENDING or AWAITING_PAYMENT
        OrderStatus current = order.getStatus();
        OrderStatus target = OrderStatus.CONFIRMED;

        if (!current.canTransitionTo(target)) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Cannot transition from " + current + " to " + target);
        }

        // Payment check for online payment
        if (order.getPaymentMethod() == PaymentMethod.ONLINE
                && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Cannot confirm online order: payment not received");
        }

        order.setStatus(target);
        order = orderRepository.save(order);

        log.info("Order confirmed: code={} by={}", order.getOrderCode(), actor);
        return buildOrderResponse(order);
    }

    /**
     * Cancel order — releases reserved stock back to available.
     *
     * <p>Per order-lifecycle.md: cancellable from PENDING, AWAITING_PAYMENT, CONFIRMED.
     * CONFIRMED orders can still be cancelled (before shipment).
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = findOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        OrderStatus target = OrderStatus.CANCELLED;

        if (!order.getStatus().canTransitionTo(target)) {
            throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL,
                    "Cannot cancel order in status " + order.getStatus());
        }

        order.setStatus(target);
        order = orderRepository.save(order);

        // Release all reserved stock for this order
        inventoryService.releaseStock("ORDER", order.getOrderCode());

        log.info("Order cancelled: code={} previousStatus={} by={}",
                order.getOrderCode(), order.getStatus(), actor);
        return buildOrderResponse(order);
    }

    /**
     * Complete order — DELIVERED → COMPLETED.
     *
     * <p>Commits reserved stock (decreases both on_hand and reserved).
     * This represents the physical goods leaving the warehouse.
     */
    @Transactional
    public OrderResponse completeOrder(Long orderId) {
        Order order = findOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        OrderStatus target = OrderStatus.COMPLETED;

        if (!order.getStatus().canTransitionTo(target)) {
            throw new AppException(ErrorCode.ORDER_CANNOT_COMPLETE,
                    "Cannot complete order in status " + order.getStatus());
        }

        order.setStatus(target);
        order = orderRepository.save(order);

        // Commit stock — decrease both on_hand and reserved
        inventoryService.completeOrder("ORDER", order.getOrderCode());

        log.info("Order completed: code={} by={}", order.getOrderCode(), actor);
        return buildOrderResponse(order);
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    private Order findOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    private OrderResponse buildOrderResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());

        OrderResponse response = orderMapper.toResponse(order);
        // Override items with properly loaded list (avoid lazy loading issues)
        OrderResponse.OrderResponseBuilder builder = OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomer().getId())
                .status(order.getStatus().name())
                .paymentMethod(order.getPaymentMethod().name())
                .paymentStatus(order.getPaymentStatus().name())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .shippingStreet(order.getShippingStreet())
                .shippingWard(order.getShippingWard())
                .shippingDistrict(order.getShippingDistrict())
                .shippingCity(order.getShippingCity())
                .shippingPostalCode(order.getShippingPostalCode())
                .subTotal(order.getSubTotal())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .voucherCode(order.getVoucherCode())
                .customerNote(order.getCustomerNote())
                .items(orderMapper.toItemResponses(items))
                .createdAt(order.getCreatedAt());

        return builder.build();
    }

    private OrderListItemResponse buildListItemResponse(Order order) {
        return OrderListItemResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus().name())
                .paymentMethod(order.getPaymentMethod().name())
                .paymentStatus(order.getPaymentStatus().name())
                .totalItems(order.getItems().stream().mapToInt(OrderItem::getQuantity).sum())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
