package com.locnguyen.ecommerce.domains.order.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.common.utils.RequestHashUtils;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.address.entity.Address;
import com.locnguyen.ecommerce.domains.address.repository.AddressRepository;
import com.locnguyen.ecommerce.domains.admin.dto.AdminOrderListItemResponse;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.carrier.dto.CheckoutCarrierQuoteResponse;
import com.locnguyen.ecommerce.domains.carrier.service.CarrierCheckoutService;
import com.locnguyen.ecommerce.domains.cart.entity.Cart;
import com.locnguyen.ecommerce.domains.cart.entity.CartItem;
import com.locnguyen.ecommerce.domains.cart.enums.CartStatus;
import com.locnguyen.ecommerce.domains.cart.repository.CartItemRepository;
import com.locnguyen.ecommerce.domains.cart.repository.CartRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.idempotency.entity.IdempotencyKey;
import com.locnguyen.ecommerce.domains.idempotency.enums.IdempotencyActionType;
import com.locnguyen.ecommerce.domains.idempotency.enums.IdempotencyStatus;
import com.locnguyen.ecommerce.domains.idempotency.service.IdempotencyService;
import com.locnguyen.ecommerce.domains.inventory.dto.ReserveStockRequest;
import com.locnguyen.ecommerce.domains.inventory.entity.Inventory;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryRepository;
import com.locnguyen.ecommerce.domains.inventory.service.InventoryService;
import com.locnguyen.ecommerce.domains.notification.enums.NotificationType;
import com.locnguyen.ecommerce.domains.notification.service.NotificationService;
import com.locnguyen.ecommerce.domains.order.dto.CreateOrderRequest;
import com.locnguyen.ecommerce.domains.order.dto.OrderAdminFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderFilter;
import com.locnguyen.ecommerce.domains.order.dto.OrderListItemResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderPreviewResponse;
import com.locnguyen.ecommerce.domains.order.dto.OrderResponse;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.mapper.OrderMapper;
import com.locnguyen.ecommerce.domains.order.repository.OrderItemRepository;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final IdempotencyService idempotencyService;
    private final CarrierCheckoutService carrierCheckoutService;

    @Override
    @Transactional(readOnly = true)
    public OrderPreviewResponse previewOrder(Customer customer, CreateOrderRequest request) {
        CheckoutDraft draft = loadCheckoutDraft(customer, request.getShippingAddressId(), false);
        Order order = buildDraftOrder(customer, draft.address(), draft.cartItems(), request, CodeGenerator.generateOrderCode());
        CheckoutCarrierQuoteResponse quote = applyShippingSelection(order, request.getCarrierId());
        return buildPreviewResponse(order, quote);
    }

    @Override
    @Transactional
    public OrderResponse createOrder(Customer customer, CreateOrderRequest request, String idempotencyKey) {
        String requestHash = RequestHashUtils.hash(request, customer.getId());
        IdempotencyKey idem = idempotencyService.findOrCreateProcessing(
                customer.getId(), IdempotencyActionType.CHECKOUT, idempotencyKey, requestHash);

        if (idem.getStatus() == IdempotencyStatus.COMPLETED) {
            UUID existingOrderId = UUID.fromString(idem.getResourceId());
            return buildOrderResponse(orderRepository.findById(existingOrderId)
                    .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND)));
        }

        try {
            return executeCheckout(customer, request, idem);
        } catch (AppException e) {
            idempotencyService.markFailed(idem.getId(), e.getErrorCode().getCode());
            throw e;
        } catch (Exception e) {
            idempotencyService.markFailed(idem.getId(), ErrorCode.INTERNAL_SERVER_ERROR.getCode());
            throw e;
        }
    }

    private OrderResponse executeCheckout(Customer customer, CreateOrderRequest request, IdempotencyKey idem) {
        CheckoutDraft draft = loadCheckoutDraft(customer, request.getShippingAddressId(), true);
        String orderCode = CodeGenerator.generateOrderCode();
        PaymentMethod paymentMethod = request.getPaymentMethod() != null
                ? request.getPaymentMethod()
                : PaymentMethod.COD;

        Order order = buildDraftOrder(customer, draft.address(), draft.cartItems(), request, orderCode);
        applyShippingSelection(order, request.getCarrierId());

        order = orderRepository.save(order);

        List<UUID> variantIds = draft.cartItems().stream()
                .map(ci -> ci.getVariant().getId())
                .toList();

        List<Inventory> allInventories = inventoryRepository.findByVariantIdIn(variantIds);
        Map<UUID, List<Inventory>> inventoriesByVariant = allInventories.stream()
                .collect(Collectors.groupingBy(inv -> inv.getVariant().getId()));

        for (CartItem ci : draft.cartItems()) {
            List<Inventory> inventories = inventoriesByVariant.get(ci.getVariant().getId());
            if (inventories == null || inventories.isEmpty()) {
                throw new AppException(ErrorCode.INVENTORY_NOT_FOUND,
                        "No inventory record found for variant " + ci.getVariant().getId());
            }

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
            reserveRequest.setExpiresAt(LocalDateTime.now().plusHours(24));

            inventoryService.reserveStock(reserveRequest);
        }

        draft.cart().setStatus(CartStatus.CHECKED_OUT);
        cartRepository.save(draft.cart());

        if (paymentMethod == PaymentMethod.COD) {
            paymentService.createCodPayment(order);
        }

        log.info("Order created: code={} customerId={} items={} total={} payment={} by={}",
                orderCode, customer.getId(), draft.cartItems().size(), order.getTotalAmount(),
                paymentMethod, SecurityUtils.getCurrentUsernameOrSystem());
        auditLogService.log(AuditAction.ORDER_CREATED, "ORDER", orderCode,
                "items=" + draft.cartItems().size() + " total=" + order.getTotalAmount());

        idempotencyService.markComplete(idem.getId(), "ORDER", order.getId().toString(), 201);
        return buildOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderListItemResponse> getMyOrders(Customer customer, OrderFilter filter, Pageable pageable) {
        Page<Order> page = orderRepository.filter(customer.getId(), filter.getStatus(), pageable);
        List<OrderListItemResponse> items = page.getContent().stream()
                .map(this::buildListItemResponse)
                .toList();
        return PagedResponse.of(items, page);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, Customer customer) {
        Order order = findOrThrow(orderId);
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }
        return buildOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByCode(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return buildOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AdminOrderListItemResponse> getAllOrders(OrderAdminFilter filter, Pageable pageable) {
        Page<Order> page = orderRepository.adminFilter(
                filter.getCustomerId(), filter.getStatus(), filter.getPaymentStatus(), pageable);
        return PagedResponse.of(page.map(this::buildAdminListItemResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdAdmin(UUID orderId) {
        return buildOrderResponse(findOrThrow(orderId));
    }

    @Override
    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        OrderStatus current = order.getStatus();
        OrderStatus target = OrderStatus.CONFIRMED;

        if (!current.canTransitionTo(target)) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Cannot transition from " + current + " to " + target);
        }

        if (order.getPaymentMethod() == PaymentMethod.ONLINE
                && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Cannot confirm online order: payment not received");
        }

        order.setStatus(target);
        order = orderRepository.save(order);

        log.info("Order confirmed: code={} by={}", order.getOrderCode(), actor);
        auditLogService.log(AuditAction.ORDER_CONFIRMED, "ORDER", order.getOrderCode());

        notificationService.send(
                order.getCustomer(),
                NotificationType.ORDER_CONFIRMED,
                "Order confirmed",
                "Your order " + order.getOrderCode() + " has been confirmed and is being prepared.",
                order.getId(),
                "ORDER"
        );

        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        return doCancelOrder(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelMyOrder(UUID orderId, Customer customer) {
        Order order = findOrThrow(orderId);

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (order.getStatus() == OrderStatus.CONFIRMED
                || !order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL,
                    "You can only cancel orders that are PENDING or AWAITING_PAYMENT");
        }

        return doCancelOrder(order);
    }

    private OrderResponse doCancelOrder(Order order) {
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        OrderStatus target = OrderStatus.CANCELLED;

        if (!order.getStatus().canTransitionTo(target)) {
            throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL,
                    "Cannot cancel order in status " + order.getStatus());
        }

        order.setStatus(target);
        order = orderRepository.save(order);

        inventoryService.releaseStock("ORDER", order.getOrderCode());

        log.info("Order cancelled: code={} by={}", order.getOrderCode(), actor);
        auditLogService.log(AuditAction.ORDER_CANCELLED, "ORDER", order.getOrderCode());

        notificationService.send(
                order.getCustomer(),
                NotificationType.ORDER_CANCELLED,
                "Order cancelled",
                "Your order " + order.getOrderCode() + " has been cancelled.",
                order.getId(),
                "ORDER"
        );

        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse completeOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        OrderStatus target = OrderStatus.COMPLETED;

        if (!order.getStatus().canTransitionTo(target)) {
            throw new AppException(ErrorCode.ORDER_CANNOT_COMPLETE,
                    "Cannot complete order in status " + order.getStatus());
        }

        order.setStatus(target);
        order = orderRepository.save(order);

        inventoryService.completeOrder("ORDER", order.getOrderCode());

        log.info("Order completed: code={} by={}", order.getOrderCode(), actor);
        auditLogService.log(AuditAction.ORDER_COMPLETED, "ORDER", order.getOrderCode());

        notificationService.send(
                order.getCustomer(),
                NotificationType.ORDER_COMPLETED,
                "Order completed",
                "Your order " + order.getOrderCode() + " is complete. You can now leave a review!",
                order.getId(),
                "ORDER"
        );

        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse processOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        applyTransition(order, OrderStatus.PROCESSING, ErrorCode.ORDER_STATUS_INVALID);
        order = orderRepository.save(order);

        log.info("Order processing: code={} by={}", order.getOrderCode(), actor);
        auditLogService.log(AuditAction.ORDER_PROCESSING, "ORDER", order.getOrderCode());
        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse deliverOrder(UUID orderId) {
        Order order = findOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        applyTransition(order, OrderStatus.DELIVERED, ErrorCode.ORDER_STATUS_INVALID);
        order = orderRepository.save(order);

        log.info("Order delivered: code={} by={}", order.getOrderCode(), actor);
        auditLogService.log(AuditAction.ORDER_DELIVERED, "ORDER", order.getOrderCode());

        notificationService.send(
                order.getCustomer(),
                NotificationType.ORDER_DELIVERED,
                "Order delivered",
                "Your order " + order.getOrderCode() + " has been delivered. Enjoy your purchase!",
                order.getId(),
                "ORDER"
        );

        return buildOrderResponse(order);
    }

    private Order findOrThrow(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    private CheckoutDraft loadCheckoutDraft(Customer customer, UUID addressId, boolean lockCart) {
        Cart cart = lockCart
                ? cartRepository.findByCustomerIdAndStatusWithLock(customer.getId(), CartStatus.ACTIVE)
                    .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND))
                : cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                    .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        List<CartItem> cartItems = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw new AppException(ErrorCode.ORDER_EMPTY);
        }

        Address address = addressRepository.findByIdAndDeletedFalse(addressId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        if (!address.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND);
        }

        return new CheckoutDraft(cart, cartItems, address);
    }

    private Order buildDraftOrder(Customer customer, Address address, List<CartItem> cartItems,
                                  CreateOrderRequest request, String orderCode) {
        PaymentMethod paymentMethod = request.getPaymentMethod() != null
                ? request.getPaymentMethod()
                : PaymentMethod.COD;

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderCode(orderCode);
        order.setStatus(paymentMethod == PaymentMethod.ONLINE
                ? OrderStatus.AWAITING_PAYMENT
                : OrderStatus.PENDING);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setCustomerNote(request.getCustomerNote());
        order.setVoucherCode(request.getVoucherCode());

        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getPhoneNumber());
        order.setShippingStreet(address.getStreetAddress());
        order.setShippingWard(address.getWard());
        order.setShippingDistrict(address.getDistrict());
        order.setShippingCity(address.getCity());
        order.setShippingPostalCode(address.getPostalCode());

        BigDecimal subTotal = BigDecimal.ZERO;
        for (CartItem ci : cartItems) {
            ProductVariant variant = ci.getVariant();
            BigDecimal effectivePrice = resolveEffectivePrice(variant);
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

        BigDecimal discountAmount = BigDecimal.ZERO;
        order.setSubTotal(subTotal);
        order.setDiscountAmount(discountAmount);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotalAmount(subTotal.subtract(discountAmount));
        return order;
    }

    private CheckoutCarrierQuoteResponse applyShippingSelection(Order order, UUID carrierId) {
        if (carrierId == null) {
            order.setCarrierId(null);
            order.setCarrierCode(null);
            order.setCarrierName(null);
            order.setCarrierProviderType(null);
            order.setShippingFee(BigDecimal.ZERO);
            order.setTotalAmount(order.getSubTotal().subtract(order.getDiscountAmount()));
            return null;
        }

        CheckoutCarrierQuoteResponse quote = carrierCheckoutService.quote(carrierId, order);
        order.setCarrierId(quote.getCarrierId());
        order.setCarrierCode(quote.getCarrierCode());
        order.setCarrierName(quote.getCarrierName());
        order.setCarrierProviderType(quote.getCarrierProviderType());
        order.setShippingFee(quote.getShippingFee() != null ? quote.getShippingFee() : BigDecimal.ZERO);
        order.setTotalAmount(order.getSubTotal()
                .subtract(order.getDiscountAmount())
                .add(order.getShippingFee()));
        return quote;
    }

    private OrderPreviewResponse buildPreviewResponse(Order order, CheckoutCarrierQuoteResponse quote) {
        return OrderPreviewResponse.builder()
                .carrierId(order.getCarrierId())
                .carrierCode(order.getCarrierCode())
                .carrierName(order.getCarrierName())
                .carrierProviderType(order.getCarrierProviderType())
                .shippingServiceName(quote != null ? quote.getServiceName() : null)
                .paymentMethod(order.getPaymentMethod())
                .subTotal(order.getSubTotal())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .voucherCode(order.getVoucherCode())
                .customerNote(order.getCustomerNote())
                .build();
    }

    private OrderResponse buildOrderResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomer().getId())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .shippingStreet(order.getShippingStreet())
                .shippingWard(order.getShippingWard())
                .shippingDistrict(order.getShippingDistrict())
                .shippingCity(order.getShippingCity())
                .shippingPostalCode(order.getShippingPostalCode())
                .carrierId(order.getCarrierId())
                .carrierCode(order.getCarrierCode())
                .carrierName(order.getCarrierName())
                .carrierProviderType(order.getCarrierProviderType())
                .subTotal(order.getSubTotal())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .voucherCode(order.getVoucherCode())
                .customerNote(order.getCustomerNote())
                .items(orderMapper.toItemResponses(items))
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderListItemResponse buildListItemResponse(Order order) {
        return OrderListItemResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .totalItems(order.getItems().stream().mapToInt(OrderItem::getQuantity).sum())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private AdminOrderListItemResponse buildAdminListItemResponse(Order order) {
        var user = order.getCustomer().getUser();
        return AdminOrderListItemResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomer().getId())
                .customerName(user.getFirstName() + " " + user.getLastName())
                .customerEmail(user.getEmail())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .totalItems(order.getItems().stream().mapToInt(OrderItem::getQuantity).sum())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private void applyTransition(Order order, OrderStatus target, ErrorCode errorCode) {
        if (!order.getStatus().canTransitionTo(target)) {
            throw new AppException(errorCode,
                    "Cannot transition from " + order.getStatus() + " to " + target);
        }
        order.setStatus(target);
    }

    private BigDecimal resolveEffectivePrice(ProductVariant variant) {
        return variant.getSalePrice() != null ? variant.getSalePrice() : variant.getBasePrice();
    }

    private record CheckoutDraft(
            Cart cart,
            List<CartItem> cartItems,
            Address address
    ) {}
}
