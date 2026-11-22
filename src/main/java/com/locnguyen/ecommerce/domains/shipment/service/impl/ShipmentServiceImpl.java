package com.locnguyen.ecommerce.domains.shipment.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProvider;
import com.locnguyen.ecommerce.domains.carrier.provider.BookingLookupResult;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderRegistry;
import com.locnguyen.ecommerce.domains.carrier.provider.ShipmentTrackingResult;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingOrderResult;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.order.service.OrderHoldService;
import com.locnguyen.ecommerce.domains.shipment.dto.CreateShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentFilter;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentResponse;
import com.locnguyen.ecommerce.domains.shipment.dto.UpdateShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.UpdateShipmentStatusRequest;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentEvent;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.BookingAttemptStatus;
import com.locnguyen.ecommerce.domains.shipment.mapper.ShipmentMapper;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentEventRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentService;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentListSummary;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentProviderUpdate;
import com.locnguyen.ecommerce.domains.shipment.specification.ShipmentSpecification;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final OrderRepository orderRepository;
    private final OrderHoldService orderHoldService;
    private final ShipmentMapper shipmentMapper;
    private final CarrierConfigRepository carrierConfigRepository;
    private final CarrierProviderRegistry carrierProviderRegistry;
    private final ShipmentBookingCoordinator bookingCoordinator;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<ShipmentListSummary> getListSummaries(List<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return shipmentRepository.findByOrderIdIn(orderIds).stream()
                .map(shipment -> new ShipmentListSummary(
                        shipment.getOrder().getId(), shipment.getStatus(), shipment.getCarrier(),
                        shipment.getTrackingNumber()))
                .toList();
    }

    @Override
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        ShipmentBookingCoordinator.PreparedBooking prepared = bookingCoordinator.prepare(request);
        if (prepared.attemptId() != null) {
            try {
                ShippingOrderResult result = prepared.provider().createShipment(
                        prepared.shipment(), prepared.shipment().getOrder(), prepared.config(), prepared.idempotencyKey());
                if (result == null) {
                    return markUnknownAndRespond(prepared,
                            new AppException(ErrorCode.CARRIER_PROVIDER_STATUS_UNKNOWN));
                }
                try {
                    bookingCoordinator.complete(prepared.attemptId(), result);
                } catch (Exception persistenceFailure) {
                    return markUnknownAndRespond(prepared, persistenceFailure);
                }
            } catch (Exception providerFailure) {
                return markUnknownAndRespond(prepared, providerFailure);
            }
        }
        return bookingCoordinator.getShipmentResponse(prepared.shipmentId());
    }

    private ShipmentResponse markUnknownAndRespond(
            ShipmentBookingCoordinator.PreparedBooking prepared, Exception failure) {
        try {
            bookingCoordinator.markUnknown(prepared.attemptId(), failure);
            return bookingCoordinator.getShipmentResponse(prepared.shipmentId());
        } catch (Exception persistenceFailure) {
            log.error("Could not persist unknown carrier outcome: shipmentId={}", prepared.shipmentId());
            Shipment shipment = prepared.shipment();
            return ShipmentResponse.builder()
                    .id(prepared.shipmentId())
                    .orderId(shipment.getOrder().getId())
                    .orderCode(shipment.getOrder().getOrderCode())
                    .shipmentCode(shipment.getShipmentCode())
                    .carrierId(shipment.getCarrierEntity() != null ? shipment.getCarrierEntity().getId() : null)
                    .carrier(shipment.getCarrier())
                    .trackingNumber(shipment.getTrackingNumber())
                    .status(shipment.getStatus())
                    .shippingFee(shipment.getShippingFee())
                    .carrierCost(shipment.getCarrierCost())
                    .estimatedCarrierCost(shipment.getEstimatedCarrierCost())
                    .bookingStatus(BookingAttemptStatus.UNKNOWN)
                    .bookingAttemptKey(prepared.idempotencyKey())
                    .note(shipment.getNote())
                    .events(List.of())
                    .createdAt(shipment.getCreatedAt())
                    .updatedAt(shipment.getUpdatedAt())
                    .build();
        }
    }

    @Override
    public ShipmentResponse reconcileBooking(UUID shipmentId) {
        var lookup = bookingCoordinator.getBookingLookup(shipmentId);
        if (lookup != null && (lookup.status() == BookingAttemptStatus.UNKNOWN
                || lookup.status() == BookingAttemptStatus.PENDING)) {
            try {
                boolean confirmedAbsent = false;
                if (lookup.provider().supportsBookingLookup()) {
                    BookingLookupResult result = lookup.provider().findShipmentByIdempotencyKey(
                            lookup.idempotencyKey(), lookup.shipmentCode(), lookup.config());
                    if (result.status() == BookingLookupResult.Status.FOUND && result.booking() != null) {
                        bookingCoordinator.complete(lookup.attemptId(), result.booking());
                        return bookingCoordinator.getShipmentResponse(shipmentId);
                    }
                    confirmedAbsent = result.status() == BookingLookupResult.Status.CONFIRMED_ABSENT;
                }
                if (lookup.provider().supportsIdempotentBooking()
                        && (!lookup.provider().supportsBookingLookup() || confirmedAbsent)) {
                    bookingCoordinator.startSafeRetry(lookup.attemptId());
                    ShippingOrderResult retried = lookup.provider().createShipment(
                            lookup.shipment(), lookup.shipment().getOrder(), lookup.config(), lookup.idempotencyKey());
                    if (retried == null) {
                        bookingCoordinator.markUnknown(lookup.attemptId(),
                                new AppException(ErrorCode.CARRIER_PROVIDER_STATUS_UNKNOWN));
                    } else {
                        bookingCoordinator.complete(lookup.attemptId(), retried);
                    }
                }
            } catch (Exception failure) {
                try {
                    bookingCoordinator.markUnknown(lookup.attemptId(), failure);
                } catch (Exception persistenceFailure) {
                    log.error("Could not persist booking reconciliation outcome: shipmentId={}", shipmentId);
                }
            }
        }
        return bookingCoordinator.getShipmentResponse(shipmentId);
    }

    @Override
    @Transactional
    public ShipmentResponse updateShipment(UUID shipmentId, UpdateShipmentRequest request) {
        Shipment shipment = findByIdOrThrow(shipmentId);

        if (request.getCarrierId() != null || request.getCarrier() != null) {
            boolean sameCarrierId = request.getCarrierId() == null
                    || (shipment.getCarrierEntity() != null
                    && request.getCarrierId().equals(shipment.getCarrierEntity().getId()));
            boolean sameManualCarrier = request.getCarrier() == null
                    || request.getCarrier().trim().equals(shipment.getCarrier());
            if (!sameCarrierId || !sameManualCarrier) {
                throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                        "Carrier changes require an audited override before booking");
            }
        }
        if (request.getTrackingNumber() != null) {
            shipment.setTrackingNumber(normalizeOptional(request.getTrackingNumber()));
        }
        if (request.getEstimatedDeliveryDate() != null) {
            shipment.setEstimatedDeliveryDate(request.getEstimatedDeliveryDate());
        }
        if (request.getShippingFee() != null) {
            shipment.setShippingFee(request.getShippingFee());
        }
        if (request.getCarrierCost() != null) {
            shipment.setCarrierCost(request.getCarrierCost());
        }
        if (request.getEstimatedCarrierCost() != null) {
            shipment.setEstimatedCarrierCost(request.getEstimatedCarrierCost());
        }
        if (request.getNote() != null) {
            shipment.setNote(normalizeOptional(request.getNote()));
        }

        shipmentRepository.save(shipment);
        log.info("Shipment updated: id={}", shipmentId);
        return shipmentMapper.toResponse(findByIdOrThrow(shipmentId));
    }

    @Override
    @Transactional
    public ShipmentResponse updateStatus(UUID shipmentId, UpdateShipmentStatusRequest request) {
        Shipment shipment = findByIdOrThrow(shipmentId);
        Order order = orderRepository.findByIdWithLock(shipment.getOrder().getId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        ensureShipmentProgressAllowed(order, request.getStatus());

        if (!shipment.getStatus().canTransitionTo(request.getStatus())) {
            throw new AppException(ErrorCode.SHIPMENT_STATUS_INVALID,
                    "Cannot transition shipment from " + shipment.getStatus()
                            + " to " + request.getStatus());
        }

        ShipmentStatus previousStatus = shipment.getStatus();
        shipment.setStatus(request.getStatus());

        LocalDateTime eventTime = request.getEventTime() != null
                ? request.getEventTime()
                : LocalDateTime.now();

        alignOrderMilestone(order, request.getStatus());
        ShipmentAuditAfterCommit.log(auditLogService, AuditAction.SHIPMENT_STATUS_UPDATED, shipmentId,
                SecurityUtils.getCurrentUsernameOrSystem(),
                "source=MANUAL; previous=" + previousStatus + "; status=" + request.getStatus());
        if (request.getStatus() == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(eventTime);
            transitionOrderStatus(order, OrderStatus.DELIVERED);
        }

        shipmentRepository.save(shipment);

        recordEvent(shipment, request.getStatus(), request.getLocation(),
                request.getDescription(), eventTime);

        log.info("Shipment status updated: id={} {} -> {}",
                shipmentId, previousStatus, request.getStatus());
        return shipmentMapper.toResponse(findByIdOrThrow(shipmentId));
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse getById(UUID shipmentId) {
        return shipmentMapper.toResponse(findByIdOrThrow(shipmentId));
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse getByOrderId(UUID orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        return shipmentMapper.toResponse(shipment);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ShipmentResponse> getShipments(ShipmentFilter filter, Pageable pageable) {
        Page<Shipment> page = shipmentRepository.findAll(
                ShipmentSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(shipmentMapper::toListItemResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentForCustomer(UUID orderId, Customer customer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        return shipmentMapper.toCustomerResponse(shipment);
    }

    @Override
    @Transactional
    public ShipmentResponse syncProviderTracking(UUID shipmentId) {
        Shipment shipment = findByIdOrThrow(shipmentId);
        CarrierProviderContext context = resolveProviderContext(shipment);
        if (!context.provider().supportsTracking()) {
            throw new AppException(ErrorCode.CARRIER_PROVIDER_NOT_SUPPORTED,
                    "Carrier does not support tracking sync");
        }
        ShipmentTrackingResult result = context.provider().getTracking(shipment, context.config());
        if (result == null) {
            return shipmentMapper.toResponse(findByIdOrThrow(shipmentId));
        }
        return applyProviderUpdate(ShipmentProviderUpdate.builder()
                .shipmentId(shipmentId)
                .status(result.status())
                .providerStatus(result.rawStatus())
                .trackingNumber(result.trackingNumber())
                .trackingUrl(result.trackingUrl())
                .location(result.location())
                .description(result.description())
                .eventTime(result.eventTime())
                .build());
    }

    @Override
    @Transactional
    public ShipmentResponse applyProviderUpdate(ShipmentProviderUpdate update) {
        Shipment shipment = findByIdOrThrow(update.shipmentId());
        Order order = orderRepository.findByIdWithLock(shipment.getOrder().getId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (update.providerStatus() != null && !update.providerStatus().isBlank()
                && update.status() != null && shouldTransition(shipment, update.status())) {
            shipment.setProviderStatus(update.providerStatus().trim());
        }
        if (update.trackingNumber() != null && !update.trackingNumber().isBlank()) {
            shipment.setTrackingNumber(update.trackingNumber().trim());
        }
        if (update.trackingUrl() != null && !update.trackingUrl().isBlank()) {
            shipment.setProviderTrackingUrl(update.trackingUrl().trim());
        }

        ShipmentStatus nextStatus = update.status();
        LocalDateTime eventTime = update.eventTime() != null ? update.eventTime() : LocalDateTime.now();
        String description = normalizeOptional(update.description());
        String location = normalizeOptional(update.location());

        if (nextStatus != null) {
            ensureShipmentProgressAllowed(order, nextStatus);
        }
        if (nextStatus != null && shouldTransition(shipment, nextStatus)) {
            shipment.setStatus(nextStatus);
            alignOrderMilestone(order, nextStatus);
            if (nextStatus == ShipmentStatus.DELIVERED) {
                shipment.setDeliveredAt(eventTime);
                transitionOrderStatus(order, OrderStatus.DELIVERED);
            }
        } else if (nextStatus != null && shipment.getStatus() != nextStatus) {
            log.warn("Skipping backward or invalid carrier status transition: shipmentId={} current={} incoming={}",
                    shipment.getId(), shipment.getStatus(), nextStatus);
        }

        shipmentRepository.save(shipment);

        if (nextStatus != null) {
            if (description == null) description = "Carrier status: " + nextStatus;
            recordEventIfAbsent(shipment, nextStatus, location, description, eventTime);
        }

        return shipmentMapper.toResponse(findByIdOrThrow(shipment.getId()));
    }

    private Shipment findByIdOrThrow(UUID id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
    }

    private ShipmentEvent recordEvent(Shipment shipment, ShipmentStatus status,
                                      String location, String description,
                                      LocalDateTime eventTime) {
        ShipmentEvent event = new ShipmentEvent();
        event.setShipment(shipment);
        event.setStatus(status);
        event.setLocation(location);
        event.setDescription(description);
        event.setEventTime(eventTime);
        return shipmentEventRepository.save(event);
    }

    private ShipmentEvent recordEventIfAbsent(Shipment shipment, ShipmentStatus status,
                                              String location, String description,
                                              LocalDateTime eventTime) {
        boolean exists = shipmentEventRepository.existsByShipmentIdAndStatusAndDescriptionAndEventTime(
                shipment.getId(), status, description, eventTime);
        if (exists) {
            return null;
        }
        return recordEvent(shipment, status, location, description, eventTime);
    }

    private void alignOrderMilestone(Order order, ShipmentStatus status) {
        if (status == ShipmentStatus.IN_TRANSIT || status == ShipmentStatus.OUT_FOR_DELIVERY
                || status == ShipmentStatus.DELIVERED) {
            if (order.getStatus() == OrderStatus.PROCESSING) transitionOrderStatus(order, OrderStatus.SHIPPED);
        }
    }

    private void transitionOrderStatus(Order order, OrderStatus target) {
        if (target == OrderStatus.SHIPPED || target == OrderStatus.DELIVERED) {
            ensureOnlinePaymentReceived(order, "fulfill");
        }
        if (!order.getStatus().canTransitionTo(target)) {
            log.warn("Skipping order status transition {} -> {} for order {}",
                    order.getStatus(), target, order.getOrderCode());
            return;
        }
        order.setStatus(target);
        orderRepository.save(order);
        log.info("Order status transitioned: orderCode={} -> {}", order.getOrderCode(), target);
    }

    private void ensureShipmentProgressAllowed(Order order, ShipmentStatus target) {
        if (target == ShipmentStatus.IN_TRANSIT
                || target == ShipmentStatus.OUT_FOR_DELIVERY
                || target == ShipmentStatus.DELIVERED) {
            ensureOnlinePaymentReceived(order, "fulfill");
        }
    }

    private void ensureOnlinePaymentReceived(Order order, String action) {
        if (order.getPaymentMethod() == PaymentMethod.ONLINE
                && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Cannot " + action + " online order: payment not received");
        }
        orderHoldService.requireFulfillmentHold(order);
    }

    private boolean requiresEnabledConfig(CarrierProviderType providerType) {
        return providerType != CarrierProviderType.MANUAL && providerType != CarrierProviderType.MOCK;
    }

    private boolean shouldTransition(Shipment shipment, ShipmentStatus nextStatus) {
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            return false;
        }
        if (shipment.getStatus() == nextStatus) {
            return false;
        }
        if (shipment.getStatus().isTerminal()) return false;
        if (nextStatus == ShipmentStatus.RETURNED) return true;
        if (shipment.getStatus() == ShipmentStatus.FAILED) return false;
        return shipment.getStatus().canTransitionTo(nextStatus)
                || (List.of(ShipmentStatus.PENDING, ShipmentStatus.PICKING, ShipmentStatus.IN_TRANSIT,
                        ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED).indexOf(nextStatus)
                        > List.of(ShipmentStatus.PENDING, ShipmentStatus.PICKING, ShipmentStatus.IN_TRANSIT,
                        ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.DELIVERED).indexOf(shipment.getStatus()));
    }

    private CarrierProviderContext resolveProviderContext(Shipment shipment) {
        Carrier carrier = shipment.getCarrierEntity();
        if (carrier == null) {
            throw new AppException(ErrorCode.CARRIER_NOT_FOUND,
                    "Shipment is not linked to a provider-based carrier");
        }
        return resolveProviderContext(carrier);
    }

    private CarrierProviderContext resolveProviderContext(Carrier carrier) {
        CarrierProvider provider = carrierProviderRegistry.find(carrier.getProviderType().name())
                .orElseThrow(() -> new AppException(ErrorCode.CARRIER_PROVIDER_NOT_SUPPORTED,
                        "No provider bean registered for " + carrier.getProviderType()));
        CarrierConfig config = carrierConfigRepository.findByCarrierId(carrier.getId()).orElse(null);
        if (requiresEnabledConfig(carrier.getProviderType())
                && (config == null || !config.isEnabled())) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                    "Carrier config is missing or disabled for " + carrier.getCode());
        }
        return new CarrierProviderContext(carrier, provider, config);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record CarrierProviderContext(
            Carrier carrier,
            CarrierProvider provider,
            CarrierConfig config
    ) {}
}
