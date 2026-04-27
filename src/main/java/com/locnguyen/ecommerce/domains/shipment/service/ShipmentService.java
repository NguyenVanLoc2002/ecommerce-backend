package com.locnguyen.ecommerce.domains.shipment.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.shipment.dto.*;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentEvent;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.shipment.mapper.ShipmentMapper;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentEventRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import com.locnguyen.ecommerce.domains.shipment.specification.ShipmentSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final OrderRepository orderRepository;
    private final ShipmentMapper shipmentMapper;

    // ─── Admin operations ─────────────────────────────────────────────────────

    /**
     * Create a shipment for an order that is in {@code PROCESSING} status.
     *
     * <p>Side-effect: transitions the order to {@code SHIPPED}.
     *
     * <p>Throws {@code SHIPMENT_ALREADY_EXISTS} if a shipment for this order
     * already exists — one shipment per order (MVP).
     */
    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Order must be in PROCESSING status to create a shipment. Current: "
                            + order.getStatus());
        }

        if (shipmentRepository.existsByOrderId(order.getId())) {
            throw new AppException(ErrorCode.SHIPMENT_ALREADY_EXISTS);
        }

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShipmentCode(CodeGenerator.generateShipmentCode());
        shipment.setCarrier(request.getCarrier().trim());
        shipment.setTrackingNumber(request.getTrackingNumber());
        shipment.setEstimatedDeliveryDate(request.getEstimatedDeliveryDate());
        shipment.setShippingFee(
                request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO);
        shipment.setNote(request.getNote());

        shipment = shipmentRepository.save(shipment);

        // Initial tracking event
        recordEvent(shipment, ShipmentStatus.PENDING, null,
                "Shipment created. Awaiting carrier pickup.", LocalDateTime.now());

        // Transition order to SHIPPED
        transitionOrderStatus(order, OrderStatus.SHIPPED);

        log.info("Shipment created: code={} orderId={} carrier={}",
                shipment.getShipmentCode(), order.getId(), shipment.getCarrier());
        return shipmentMapper.toResponse(findByIdOrThrow(shipment.getId()));
    }

    /**
     * Update editable shipment fields (carrier, tracking number, dates, note).
     * Does not change status — use {@link #updateStatus} for that.
     */
    @Transactional
    public ShipmentResponse updateShipment(Long shipmentId, UpdateShipmentRequest request) {
        Shipment shipment = findByIdOrThrow(shipmentId);

        if (request.getCarrier() != null) {
            shipment.setCarrier(request.getCarrier().trim());
        }
        if (request.getTrackingNumber() != null) {
            shipment.setTrackingNumber(request.getTrackingNumber().trim());
        }
        if (request.getEstimatedDeliveryDate() != null) {
            shipment.setEstimatedDeliveryDate(request.getEstimatedDeliveryDate());
        }
        if (request.getShippingFee() != null) {
            shipment.setShippingFee(request.getShippingFee());
        }
        if (request.getNote() != null) {
            shipment.setNote(request.getNote());
        }

        shipmentRepository.save(shipment);
        log.info("Shipment updated: id={}", shipmentId);
        return shipmentMapper.toResponse(findByIdOrThrow(shipmentId));
    }

    /**
     * Advance the shipment status and append a tracking event.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Validates the transition via {@link ShipmentStatus#canTransitionTo}</li>
     *   <li>On {@code DELIVERED}: sets {@code deliveredAt}, transitions order to DELIVERED</li>
     *   <li>On {@code RETURNED}: transitions order back to a resolvable state (admin notes)</li>
     * </ul>
     */
    @Transactional
    public ShipmentResponse updateStatus(Long shipmentId, UpdateShipmentStatusRequest request) {
        Shipment shipment = findByIdOrThrow(shipmentId);

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

        if (request.getStatus() == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(eventTime);
            transitionOrderStatus(shipment.getOrder(), OrderStatus.DELIVERED);
        }

        shipmentRepository.save(shipment);

        recordEvent(shipment, request.getStatus(), request.getLocation(),
                request.getDescription(), eventTime);

        log.info("Shipment status updated: id={} {} → {}",
                shipmentId, previousStatus, request.getStatus());
        return shipmentMapper.toResponse(findByIdOrThrow(shipmentId));
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getById(Long shipmentId) {
        return shipmentMapper.toResponse(findByIdOrThrow(shipmentId));
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getByOrderId(Long orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        return shipmentMapper.toResponse(shipment);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ShipmentResponse> getShipments(ShipmentFilter filter, Pageable pageable) {
        Page<Shipment> page = shipmentRepository.findAll(
                ShipmentSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(shipmentMapper::toListItemResponse));
    }

    // ─── Customer operations ──────────────────────────────────────────────────

    /**
     * Get the shipment for a customer's own order.
     * Enforces ownership — throws {@code ORDER_NOT_FOUND} if the order belongs to another customer.
     */
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentForCustomer(Long orderId, Customer customer) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }

        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        return shipmentMapper.toResponse(shipment);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private Shipment findByIdOrThrow(Long id) {
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

    private void transitionOrderStatus(Order order, OrderStatus target) {
        if (!order.getStatus().canTransitionTo(target)) {
            log.warn("Skipping order status transition {} → {} for order {}",
                    order.getStatus(), target, order.getOrderCode());
            return;
        }
        order.setStatus(target);
        orderRepository.save(order);
        log.info("Order status transitioned: orderCode={} → {}", order.getOrderCode(), target);
    }
}
