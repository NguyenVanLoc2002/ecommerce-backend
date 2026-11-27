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
import com.locnguyen.ecommerce.domains.returns.enums.ReturnPolicy;
import com.locnguyen.ecommerce.domains.order.dto.ExchangePriceSnapshot;
import com.locnguyen.ecommerce.domains.order.dto.ReturnFinancialLine;
import com.locnguyen.ecommerce.domains.order.dto.CreateOrderRequest;
import com.locnguyen.ecommerce.domains.order.dto.CancellationOrderContext;
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
import com.locnguyen.ecommerce.domains.order.repository.OrderItemCountProjection;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.order.service.tax.OrderTaxEstimates;
import com.locnguyen.ecommerce.domains.order.service.OrderHoldService;
import com.locnguyen.ecommerce.domains.order.service.CheckoutHoldPolicy;
import com.locnguyen.ecommerce.domains.invoice.service.InvoiceService;
import com.locnguyen.ecommerce.domains.payment.service.PaymentService;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentService;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentListSummary;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.product.entity.ProductMedia;
import com.locnguyen.ecommerce.domains.product.enums.MediaType;
import com.locnguyen.ecommerce.domains.product.repository.ProductMediaRepository;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.promotion.dto.CheckoutVoucher;
import com.locnguyen.ecommerce.domains.promotion.dto.VoucherLine;
import com.locnguyen.ecommerce.domains.order.service.OrderVoucherAllocation;
import com.locnguyen.ecommerce.domains.promotion.dto.ValidateVoucherResponse;
import com.locnguyen.ecommerce.domains.promotion.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
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
    private final OrderHoldService orderHoldService;
    private final InventoryRepository inventoryRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final IdempotencyService idempotencyService;
    private final CarrierCheckoutService carrierCheckoutService;
    private final ProductMediaRepository productMediaRepository;
    private final VoucherService voucherService;
    private final ShipmentService shipmentService;
    private final InvoiceService invoiceService;
    private final OrderTaxEstimates orderTaxEstimates;

    private static final int CUSTOMER_ORDER_ITEM_PREVIEW_LIMIT = 3;
    private static final Set<String> ADMIN_ORDER_SORT_FIELDS = Set.of("orderCode", "status", "createdAt", "totalAmount");

    @Override
    @Transactional(readOnly = true)
    public OrderPreviewResponse previewOrder(Customer customer, CreateOrderRequest request) {
        CheckoutDraft draft = loadCheckoutDraft(customer, request.getShippingAddressId(), false);
        if (request.getPaymentMethod() == PaymentMethod.EXCHANGE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Replacement orders are created from an approved exchange");
        }
        Order order = buildDraftOrder(customer, draft.address(), draft.cartItems(), request, CodeGenerator.generateOrderCode());
        applyVoucherPreview(order, customer, request.getVoucherCode());
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

        if (paymentMethod == PaymentMethod.EXCHANGE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Replacement orders are created from an approved exchange");
        }
        Order order = buildDraftOrder(customer, draft.address(), draft.cartItems(), request, orderCode);
        snapshotExchangePrices(order);
        order = orderRepository.save(order);
        applyVoucherDuringCheckout(order, customer, request.getVoucherCode());
        applyShippingSelection(order, request.getCarrierId());
        if (orderTaxEstimates.enabled()) {
            orderRepository.flush();
            orderTaxEstimates.capture(order);
        }

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
            reserveRequest.setExpiresAt(LocalDateTime.now().plus(CheckoutHoldPolicy.waitFor(paymentMethod)));

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
        if (page.isEmpty()) {
            return PagedResponse.of(List.of(), page);
        }

        List<UUID> orderIds = page.getContent().stream().map(Order::getId).toList();
        Map<UUID, List<OrderItem>> itemsByOrder = orderItemRepository
                .findByOrderIdInOrderByCreatedAtAscIdAsc(orderIds).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        List<OrderListItemResponse> items = page.getContent().stream()
                .map(order -> buildListItemResponse(order, itemsByOrder.getOrDefault(order.getId(), List.of())))
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
    @Transactional
    public Optional<UUID> createExchangeReplacement(UUID orderId, UUID itemId, UUID exchangeId, UUID variantId, int quantity) {
        Order original = findForUpdateOrThrow(orderId);
        OrderItem purchased = orderItemRepository.findById(itemId)
                .filter(item -> item.getOrder().getId().equals(orderId))
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_NOT_ELIGIBLE));
        String code = CodeGenerator.generateOrderCode();
        var hold = inventoryService.transferExchangeStock(exchangeId, variantId, quantity, code);
        if (hold.isEmpty()) {
            return Optional.empty();
        }

        ProductVariant alternate = hold.get();
        Order replacement = new Order();
        replacement.setOrderCode(code);
        replacement.setReplacementForOrderId(orderId);
        replacement.setCustomer(original.getCustomer());
        replacement.setCustomerNameSnapshot(original.getCustomerNameSnapshot());
        replacement.setCustomerEmailSnapshot(original.getCustomerEmailSnapshot());
        replacement.setReceiverName(original.getReceiverName());
        replacement.setReceiverPhone(original.getReceiverPhone());
        replacement.setShippingStreet(original.getShippingStreet());
        replacement.setShippingWard(original.getShippingWard());
        replacement.setShippingDistrict(original.getShippingDistrict());
        replacement.setShippingCity(original.getShippingCity());
        replacement.setShippingPostalCode(original.getShippingPostalCode());
        replacement.setPaymentMethod(PaymentMethod.EXCHANGE);
        replacement.setPaymentStatus(PaymentStatus.NOT_REQUIRED);
        replacement.setStatus(OrderStatus.CONFIRMED);
        replacement.setSubTotal(BigDecimal.ZERO);
        replacement.setTotalAmount(BigDecimal.ZERO);

        OrderItem item = new OrderItem();
        item.setOrder(replacement);
        item.setVariant(alternate);
        item.setProductName(purchased.getProductName());
        item.setVariantName(alternate.getVariantName());
        item.setSku(alternate.getSku());
        item.setProductImageUrl(resolveOrderItemImageUrl(alternate));
        item.setUnitPrice(BigDecimal.ZERO);
        item.setQuantity(quantity);
        item.setLineTotal(BigDecimal.ZERO);
        item.setNetLineAmount(BigDecimal.ZERO);
        item.setReturnPolicySnapshot(purchased.getReturnPolicySnapshot());
        replacement.getItems().add(item);
        return Optional.of(orderRepository.save(replacement).getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReturnFinancialLine> getReturnFinancialLines(UUID orderId) {
        return orderItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(item -> new ReturnFinancialLine(item.getId(), item.getQuantity(), item.getNetLineAmount())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExchangePriceSnapshot getExchangePriceSnapshot(UUID orderId, UUID itemId) {
        return orderItemRepository.findById(itemId)
                .filter(item -> item.getOrder().getId().equals(orderId))
                .map(item -> new ExchangePriceSnapshot(item.getUnitPrice(), item.getExchangeBasePrices()))
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_NOT_ELIGIBLE));
    }

    @Override
    @Transactional
    public OrderStatus lockOrderForReturn(UUID orderId) {
        return findForUpdateOrThrow(orderId).getStatus();
    }

    @Override
    @Transactional
    public OrderResponse lockOwnedOrderForReturn(UUID orderId, Customer customer) {
        Order order = findForUpdateOrThrow(orderId);
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }
        return buildOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByCode(String orderCode) {
        Order order = orderRepository.findAdminByOrderCodeWithCurrentCustomer(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return buildAdminOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AdminOrderListItemResponse> getAllOrders(OrderAdminFilter filter, Pageable pageable) {
        if (filter.getCreatedFrom() != null && filter.getCreatedTo() != null
                && filter.getCreatedFrom().isAfter(filter.getCreatedTo())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "createdFrom must be before or equal to createdTo");
        }
        Pageable safePageable = withWhitelistedAdminSort(pageable);
        String keyword = filter.getKeyword() == null || filter.getKeyword().isBlank()
                ? null : filter.getKeyword().trim();
        Page<Order> page = orderRepository.adminSearch(
                filter.getCustomerId(), filter.getStatus(), filter.getPaymentStatus(),
                filter.getPaymentMethod(), filter.getCreatedFrom(), filter.getCreatedTo(), keyword,
                filter.getShipmentStatus(), safePageable);
        List<UUID> orderIds = page.getContent().stream().map(Order::getId).toList();
        Map<UUID, Integer> itemCounts = orderIds.isEmpty() ? Map.of() :
                orderItemRepository.countQuantitiesByOrderIds(orderIds).stream().collect(Collectors.toMap(
                        OrderItemCountProjection::getOrderId,
                        count -> count.getTotalQuantity() == null ? 0 : count.getTotalQuantity().intValue()));
        Map<UUID, ShipmentListSummary> shipmentSummaries = shipmentService.getListSummaries(orderIds).stream()
                .collect(Collectors.toMap(ShipmentListSummary::orderId, summary -> summary));
        return PagedResponse.of(page.map(order -> buildAdminListItemResponse(order,
                itemCounts.getOrDefault(order.getId(), 0), shipmentSummaries.get(order.getId()))));
    }

    private Pageable withWhitelistedAdminSort(Pageable pageable) {
        Sort sort = pageable.getSort().isUnsorted() ? Sort.by(Sort.Order.desc("createdAt")) :
                Sort.by(pageable.getSort().stream().map(order -> {
                    if (!ADMIN_ORDER_SORT_FIELDS.contains(order.getProperty())) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Unsupported order sort field: " + order.getProperty());
                    }
                    return order;
                }).toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdAdmin(UUID orderId) {
        Order order = orderRepository.findAdminByIdWithCurrentCustomer(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return buildAdminOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        Order order = findForUpdateOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        OrderStatus current = order.getStatus();
        OrderStatus target = OrderStatus.CONFIRMED;

        if (current == OrderStatus.PENDING && order.getPaymentMethod() != PaymentMethod.COD) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Only COD orders can be confirmed directly from PENDING");
        }

        if (!current.canTransitionTo(target)) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Cannot transition from " + current + " to " + target);
        }

        ensureOnlinePaymentReceived(order, "confirm");
        if (order.getPaymentMethod() == PaymentMethod.COD) orderHoldService.retainForFulfillment(order);
        else orderHoldService.requireFulfillmentHold(order);

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
        Order order = findForUpdateOrThrow(orderId);
        return doCancelOrder(order);
    }

    @Override
    @Transactional(readOnly = true)
    public CancellationOrderContext getCancellationContext(UUID orderId) {
        Order order = findOrThrow(orderId);
        return cancellationContext(order);
    }

    @Override
    @Transactional
    public CancellationOrderContext lockCancellationOrder(UUID orderId) {
        return cancellationContext(findForUpdateOrThrow(orderId));
    }

    @Override
    @Transactional
    public CancellationOrderContext finalizeCancellation(UUID orderId, String actor, String reason) {
        Order order = findForUpdateOrThrow(orderId);
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.COMPLETED
                || order.getStatus() == OrderStatus.REFUNDED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL,
                    "Delivered or terminal orders require a return or refund workflow");
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        invoiceService.voidByOrderId(order.getId(), "Invoice voided because order " + order.getOrderCode() + " was cancelled: " + reason);
        inventoryService.releaseStock("ORDER", order.getOrderCode());
        notificationService.send(order.getCustomer(), NotificationType.ORDER_CANCELLED,
                "Order cancelled", "Your order " + order.getOrderCode() + " has been cancelled.",
                order.getId(), "ORDER");
        auditLogService.logWithActor(AuditAction.ORDER_CANCELLED, "ORDER", order.getOrderCode(), actor,
                "Cancellation confirmed; reason=" + reason);
        return cancellationContext(order);
    }

    private CancellationOrderContext cancellationContext(Order order) {
        return new CancellationOrderContext(order.getId(), order.getOrderCode(), order.getStatus());
    }

    @Override
    @Transactional
    public OrderResponse cancelMyOrder(UUID orderId, Customer customer) {
        Order order = findForUpdateOrThrow(orderId);

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

        if (order.getPaymentMethod() == PaymentMethod.ONLINE
                && order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL,
                    "Cannot cancel a paid online order without a refund");
        }

        order.setStatus(target);
        order = orderRepository.save(order);
        invoiceService.voidByOrderId(order.getId(), "Invoice voided because order " + order.getOrderCode() + " was cancelled");

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
        Order order = findForUpdateOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        ensureOnlinePaymentReceived(order, "complete");
        orderHoldService.requireFulfillmentHold(order);

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
        Order order = findForUpdateOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        ensureOnlinePaymentReceived(order, "process");
        orderHoldService.requireFulfillmentHold(order);
        applyTransition(order, OrderStatus.PROCESSING, ErrorCode.ORDER_STATUS_INVALID);
        order = orderRepository.save(order);

        log.info("Order processing: code={} by={}", order.getOrderCode(), actor);
        auditLogService.log(AuditAction.ORDER_PROCESSING, "ORDER", order.getOrderCode());
        return buildOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse deliverOrder(UUID orderId) {
        Order order = findForUpdateOrThrow(orderId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        ensureOnlinePaymentReceived(order, "deliver");
        orderHoldService.requireFulfillmentHold(order);
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

    private Order findForUpdateOrThrow(UUID id) {
        return orderRepository.findByIdWithLock(id)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void ensureOnlinePaymentReceived(Order order, String action) {
        if (order.getPaymentMethod() == PaymentMethod.ONLINE
                && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Cannot " + action + " online order: payment not received");
        }
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
        var customerUser = customer.getUser();
        order.setCustomerNameSnapshot(joinName(customerUser.getFirstName(), customerUser.getLastName()));
        order.setCustomerEmailSnapshot(customerUser.getEmail());
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
            item.setProductImageUrl(resolveOrderItemImageUrl(variant));
            item.setUnitPrice(variant.getBasePrice());
            item.setSalePrice(variant.getSalePrice());
            item.setQuantity(ci.getQuantity());
            item.setLineTotal(lineTotal);
            var product = variant.getProduct();
            item.setReturnPolicySnapshot(ReturnPolicy.resolve(product.getReturnPolicy(), product.getCategories().stream()
                    .filter(category -> !category.isDeleted()).map(category -> category.getReturnPolicy())));

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

    private String resolveOrderItemImageUrl(ProductVariant variant) {
        UUID productId = variant.getProduct().getId();
        UUID variantId = variant.getId();

        String variantImage = selectPreferredImage(productMediaRepository
                .findByProductIdAndVariantIdAndMediaTypeOrderBySortOrderAsc(
                        productId, variantId, MediaType.IMAGE));
        if (variantImage != null) {
            return variantImage;
        }

        return selectPreferredImage(productMediaRepository
                .findByProductIdAndVariantIsNullAndMediaTypeOrderBySortOrderAsc(
                        productId, MediaType.IMAGE));
    }

    private String selectPreferredImage(List<ProductMedia> media) {
        return media.stream()
                .filter(ProductMedia::isPrimary)
                .map(ProductMedia::getMediaUrl)
                .findFirst()
                .orElseGet(() -> media.stream()
                        .map(ProductMedia::getMediaUrl)
                        .findFirst()
                        .orElse(null));
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

    @Override
    @Transactional(readOnly = true)
    public ValidateVoucherResponse quoteCartVoucher(Customer customer, String voucherCode) {
        Cart cart = cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));
        List<CartItem> items = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        if (items.isEmpty()) {
            throw new AppException(ErrorCode.ORDER_EMPTY);
        }
        List<VoucherLine> lines = items.stream().map(item -> voucherLine(item.getVariant(),
                resolveEffectivePrice(item.getVariant()).multiply(BigDecimal.valueOf(item.getQuantity())))).toList();
        return voucherService.quoteCheckoutVoucher(voucherCode, customer, lines).quote();
    }

    private void applyVoucherPreview(Order order, Customer customer, String voucherCode) {
        if (voucherCode != null && !voucherCode.isBlank()) {
            applyVoucherTotals(order, voucherService.quoteCheckoutVoucher(voucherCode, customer, voucherLines(order)));
        }
    }

    private void applyVoucherDuringCheckout(Order order, Customer customer, String voucherCode) {
        java.util.Set<OrderItem> eligible = java.util.Set.of();
        if (voucherCode != null && !voucherCode.isBlank()) {
            CheckoutVoucher voucher = voucherService.redeemCheckoutVoucher(
                    voucherCode, customer, order.getId(), voucherLines(order));
            applyVoucherTotals(order, voucher);
            eligible = order.getItems().stream()
                    .filter(item -> voucher.eligibleVariantIds().contains(item.getVariant().getId()))
                    .collect(Collectors.toUnmodifiableSet());
        }
        var allocations = OrderVoucherAllocation.resolve(order, eligible);
        for (OrderItem item : order.getItems()) {
            BigDecimal discount = allocations.get(item);
            item.setVoucherDiscountAmount(discount);
            item.setNetLineAmount(item.getLineTotal().subtract(discount));
            item.setVoucherEligible(eligible.contains(item));
            item.setVoucherAllocationVersion(1);
        }
    }

    private void applyVoucherTotals(Order order, CheckoutVoucher voucher) {
        order.setVoucherCode(voucher.quote().getVoucherCode());
        order.setDiscountAmount(voucher.quote().getDiscountAmount());
        order.setTotalAmount(order.getSubTotal().subtract(order.getDiscountAmount()));
    }

    private List<VoucherLine> voucherLines(Order order) {
        return order.getItems().stream().map(item -> voucherLine(item.getVariant(), item.getLineTotal())).toList();
    }

    private VoucherLine voucherLine(ProductVariant variant, BigDecimal lineTotal) {
        var product = variant.getProduct();
        return new VoucherLine(variant.getId(), product.getId(),
                product.getCategories().stream().map(category -> category.getId()).collect(Collectors.toSet()),
                product.getBrand() == null ? null : product.getBrand().getId(), lineTotal);
    }

    private void snapshotExchangePrices(Order order) {
        for (OrderItem item : order.getItems()) {
            Map<UUID, BigDecimal> prices = new java.util.HashMap<>();
            for (ProductVariant candidate : item.getVariant().getProduct().getVariants()) {
                if (!candidate.isDeleted() && candidate.getId() != null && candidate.getBasePrice() != null) {
                    prices.put(candidate.getId(), candidate.getBasePrice());
                }
            }
            prices.put(item.getVariant().getId(), item.getUnitPrice());
            item.setExchangeBasePrices(prices);
        }
    }

    private OrderPreviewResponse buildPreviewResponse(Order order, CheckoutCarrierQuoteResponse quote) {
        return OrderPreviewResponse.builder()
                .returnPolicies(order.getItems().stream().map(item -> new OrderPreviewResponse.ReturnPolicyLine(
                        item.getVariant().getId(), item.getReturnPolicySnapshot())).toList())
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
                .replacementForOrderId(order.getReplacementForOrderId())
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomerId())
                .customerNameSnapshot(order.getCustomerNameSnapshot())
                .customerEmailSnapshot(order.getCustomerEmailSnapshot())
                .customerSnapshotAvailable(order.getCustomerNameSnapshot() != null || order.getCustomerEmailSnapshot() != null)
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

    private OrderResponse buildAdminOrderResponse(Order order) {
        var customer = order.getCustomer();
        var user = customer == null ? null : customer.getUser();
        return buildOrderResponse(order).toBuilder()
                .currentCustomerName(user == null ? null : joinName(user.getFirstName(), user.getLastName()))
                .currentCustomerEmail(user == null ? null : user.getEmail())
                .currentCustomerProfileAvailable(user != null)
                .build();
    }

    private OrderListItemResponse buildListItemResponse(Order order, List<OrderItem> items) {
        return OrderListItemResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .totalItems(items.stream().mapToInt(OrderItem::getQuantity).sum())
                .itemsPreview(orderMapper.toItemResponses(items.stream().limit(CUSTOMER_ORDER_ITEM_PREVIEW_LIMIT).toList()))
                .remainingItemLines(Math.max(0, items.size() - CUSTOMER_ORDER_ITEM_PREVIEW_LIMIT))
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private AdminOrderListItemResponse buildAdminListItemResponse(Order order, int totalItems,
                                                                   ShipmentListSummary shipment) {
        var profileAvailable = order.getCustomer() != null && order.getCustomer().getUser() != null;
        var user = profileAvailable ? order.getCustomer().getUser() : null;
        return AdminOrderListItemResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomerId())
                .customerNameSnapshot(order.getCustomerNameSnapshot())
                .customerEmailSnapshot(order.getCustomerEmailSnapshot())
                .customerSnapshotAvailable(order.getCustomerNameSnapshot() != null || order.getCustomerEmailSnapshot() != null)
                .customerName(profileAvailable ? joinName(user.getFirstName(), user.getLastName()) : null)
                .customerEmail(profileAvailable ? user.getEmail() : null)
                .currentCustomerProfileAvailable(profileAvailable)
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .totalItems(totalItems)
                .shipmentStatus(shipment == null ? null : shipment.status())
                .carrier(shipment == null ? null : shipment.carrier())
                .trackingNumber(shipment == null ? null : shipment.trackingNumber())
                .attention(resolveOrderAttention(order, shipment))
                .attentionReason(resolveOrderAttentionReason(order, shipment))
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private String resolveOrderAttention(Order order, ShipmentListSummary shipment) {
        if (order.getStatus() == OrderStatus.CANCELLED
                && (order.getPaymentStatus() == PaymentStatus.PAID
                || order.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED))
            return "REFUND_REQUIRED";
        if (order.getPaymentStatus() == PaymentStatus.FAILED) return "PAYMENT_FAILED";
        if (order.getStatus() != OrderStatus.CANCELLED && shipment != null
                && shipment.status() == ShipmentStatus.FAILED) return "DELIVERY_FAILED";
        if (shipment != null && shipment.status() == ShipmentStatus.RETURNED) return "RETURNED_HANDLING";
        if (order.getStatus() == OrderStatus.DELIVERED) return "DELIVERED_AWAITING_COMPLETION";
        if ((order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PROCESSING)
                && shipment == null) return "READY_TO_SHIP";
        return null;
    }

    private String resolveOrderAttentionReason(Order order, ShipmentListSummary shipment) {
        String attention = resolveOrderAttention(order, shipment);
        return switch (attention == null ? "" : attention) {
            case "REFUND_REQUIRED" -> "Payment received after cancellation; reconcile and refund";
            case "PAYMENT_FAILED" -> "Payment failed";
            case "DELIVERY_FAILED" -> "Carrier delivery attempt failed";
            case "RETURNED_HANDLING" -> "Return requires handling";
            case "DELIVERED_AWAITING_COMPLETION" -> "Delivered order is awaiting completion";
            case "READY_TO_SHIP" -> "Order is ready to ship";
            default -> null;
        };
    }

    private String joinName(String firstName, String lastName) {
        return java.util.stream.Stream.of(firstName, lastName)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
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
