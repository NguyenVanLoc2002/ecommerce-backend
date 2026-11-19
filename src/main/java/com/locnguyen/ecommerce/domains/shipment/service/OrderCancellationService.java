package com.locnguyen.ecommerce.domains.shipment.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProvider;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderRegistry;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.order.dto.CancellationOrderContext;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.shipment.dto.CancelOrderRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.CancellationOperationResponse;
import com.locnguyen.ecommerce.domains.shipment.dto.ReconcileCancellationRequest;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentCancellationOperation;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentEvent;
import com.locnguyen.ecommerce.domains.shipment.enums.CancellationOperationStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.BookingAttemptStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentCancellationOperationRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentEventRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final OrderService orderService;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentCancellationOperationRepository operationRepository;
    private final ShipmentEventRepository eventRepository;
    private final CarrierProviderRegistry providerRegistry;
    private final CarrierConfigRepository carrierConfigRepository;
    private final AuditLogService auditLogService;
    private final ObjectProvider<OrderCancellationService> self;

    public CancellationOperationResponse cancelOrder(UUID orderId, CancelOrderRequest request) {
        StartResult start = self.getObject().start(orderId, request.getReason());
        if (start.existing()) return response(start.operation());
        if (start.operation().getStatus() == CancellationOperationStatus.UNKNOWN) {
            return response(start.operation());
        }
        if (start.provider() == null) {
            return response(self.getObject().confirm(start.operation().getId(), "Cancellation confirmed internally."));
        }

        boolean confirmed;
        try {
            confirmed = start.interception()
                    ? start.provider().interceptShipment(start.shipment(), request.getReason(), start.config())
                    : start.provider().cancelShipment(start.shipment(), request.getReason(), start.config());
        } catch (RuntimeException exception) {
            return response(self.getObject().markUnknown(start.operation().getId(),
                    "PROVIDER_CANCELLATION_OUTCOME_UNKNOWN"));
        }
        if (!confirmed) return response(self.getObject().reject(start.operation().getId(),
                "PROVIDER_CANCELLATION_NOT_CONFIRMED"));
        return response(self.getObject().confirm(start.operation().getId(), "Carrier cancellation confirmed."));
    }

    public CancellationOperationResponse cancelShipment(UUID shipmentId, CancelOrderRequest request) {
        UUID orderId = shipmentRepository.findOrderIdByShipmentId(shipmentId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        return cancelOrder(orderId, request);
    }

    @Transactional(readOnly = true)
    public CancellationOperationResponse getByOrderId(UUID orderId) {
        return operationRepository.findByOrderId(orderId).map(this::response).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CancellationOperationResponse reconcile(UUID orderId, ReconcileCancellationRequest request) {
        ShipmentCancellationOperation operation = operationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND, "Cancellation operation not found"));
        if (operation.getStatus() == CancellationOperationStatus.CONFIRMED
                || operation.getStatus() == CancellationOperationStatus.REJECTED) {
            return response(operation);
        }
        if (request.isConfirmed()) {
            return response(confirmLocked(operation, "Manual reconciliation: " + request.getNote()));
        }
        operation.setStatus(CancellationOperationStatus.REJECTED);
        operation.setLastErrorCode("MANUALLY_RECONCILED_NOT_CANCELLED");
        operation.setCompletedAt(LocalDateTime.now());
        operationRepository.save(operation);
        CancellationOrderContext order = orderService.getCancellationContext(orderId);
        auditLogService.logWithActor(AuditAction.ORDER_CANCELLATION_RECONCILED, "ORDER", order.orderCode(),
                SecurityUtils.getCurrentUsernameOrSystem(), "Cancellation manually reconciled as not cancelled: "
                        + request.getNote());
        return response(operation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StartResult start(UUID orderId, String reason) {
        CancellationOrderContext order = orderService.lockCancellationOrder(orderId);
        ShipmentCancellationOperation previous = operationRepository.findByOrderId(orderId).orElse(null);
        if (previous != null) return new StartResult(previous, null, null, null, false, true);
        if (order.status() == OrderStatus.DELIVERED || order.status() == OrderStatus.COMPLETED
                || order.status() == OrderStatus.REFUNDED || order.status() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL,
                    "Delivered or terminal orders require a return or refund workflow");
        }

        Shipment shipment = shipmentRepository.findByOrderIdWithLock(orderId).orElse(null);
        CarrierProvider provider = null;
        CarrierConfig config = null;
        boolean interception = false;
        boolean bookingUnknown = false;
        if (shipment != null) {
            if (shipment.getStatus() == ShipmentStatus.DELIVERED || shipment.getStatus() == ShipmentStatus.RETURNED) {
                throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL,
                        "Delivered or returned shipments require a return workflow");
            }
            boolean providerBacked = isProviderBacked(shipment);
            if ((shipment.getStatus() == ShipmentStatus.IN_TRANSIT
                    || shipment.getStatus() == ShipmentStatus.OUT_FOR_DELIVERY)
                    && !providerBacked) {
                throw new AppException(ErrorCode.CARRIER_PROVIDER_NOT_SUPPORTED,
                        "Internal shipments cannot be intercepted after carrier handoff");
            }
            if (!providerBacked
                    || shipment.getBookingStatus() == BookingAttemptStatus.INTERNAL_ONLY
                    || shipment.getBookingStatus() == BookingAttemptStatus.FAILED_FINAL) {
                provider = null;
            } else if (shipment.getBookingStatus() == BookingAttemptStatus.SUCCEEDED) {
                provider = providerRegistry.find(shipment.getCarrierEntity().getProviderType().name())
                        .orElseThrow(() -> new AppException(ErrorCode.CARRIER_PROVIDER_NOT_SUPPORTED));
                config = carrierConfigRepository.findByCarrierId(shipment.getCarrierEntity().getId()).orElse(null);
                interception = shipment.getStatus() == ShipmentStatus.IN_TRANSIT
                        || shipment.getStatus() == ShipmentStatus.OUT_FOR_DELIVERY;
                if (!interception && shipment.getStatus() != ShipmentStatus.PENDING
                        && shipment.getStatus() != ShipmentStatus.PICKING) {
                    throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL,
                            "Provider cancellation is only available before carrier pickup");
                }
                if (interception && !provider.capabilities().interception()) {
                    throw new AppException(ErrorCode.CARRIER_PROVIDER_NOT_SUPPORTED,
                            "Carrier does not support intercept or return-to-sender");
                }
                if (!interception && !provider.supportsCancellation()) {
                    throw new AppException(ErrorCode.CARRIER_PROVIDER_NOT_SUPPORTED,
                            "Carrier does not support cancellation");
                }
            } else {
                bookingUnknown = true;
            }
        }

        ShipmentCancellationOperation operation = new ShipmentCancellationOperation();
        operation.setOrderId(orderId);
        operation.setShipment(shipment);
        operation.setIdempotencyKey(UUID.randomUUID().toString());
        operation.setReason(reason.trim());
        operation.setRequestedBy(SecurityUtils.getCurrentUsernameOrSystem());
        operation.setStatus(bookingUnknown ? CancellationOperationStatus.UNKNOWN : CancellationOperationStatus.PENDING);
        if (operation.getStatus() == CancellationOperationStatus.UNKNOWN) {
            operation.setLastErrorCode("BOOKING_OUTCOME_UNKNOWN");
        }
        operation = operationRepository.saveAndFlush(operation);
        auditLogService.logWithActor(AuditAction.ORDER_CANCELLATION_REQUESTED, "ORDER", order.orderCode(),
                operation.getRequestedBy(), "Cancellation requested; operationId=" + operation.getId()
                        + "; reason=" + operation.getReason());
        if (operation.getStatus() == CancellationOperationStatus.UNKNOWN) {
            return new StartResult(operation, null, shipment, null, false, false);
        }
        return new StartResult(operation, provider, shipment, config, interception, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShipmentCancellationOperation confirm(UUID operationId, String note) {
        ShipmentCancellationOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (operation.getStatus() == CancellationOperationStatus.CONFIRMED) return operation;
        return confirmLocked(operation, note);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShipmentCancellationOperation reject(UUID operationId, String code) {
        ShipmentCancellationOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (operation.getStatus() == CancellationOperationStatus.CONFIRMED) return operation;
        operation.setStatus(CancellationOperationStatus.REJECTED);
        operation.setLastErrorCode(code);
        operation.setCompletedAt(LocalDateTime.now());
        return operationRepository.save(operation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShipmentCancellationOperation markUnknown(UUID operationId, String code) {
        ShipmentCancellationOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (operation.getStatus() == CancellationOperationStatus.CONFIRMED) return operation;
        operation.setStatus(CancellationOperationStatus.UNKNOWN);
        operation.setLastErrorCode(code);
        operation.setCompletedAt(null);
        return operationRepository.save(operation);
    }

    private ShipmentCancellationOperation confirmLocked(ShipmentCancellationOperation operation, String note) {
        if (operation.getStatus() == CancellationOperationStatus.CONFIRMED) return operation;
        orderService.lockCancellationOrder(operation.getOrderId());
        if (operation.getShipment() != null) {
            Shipment shipment = shipmentRepository.findById(operation.getShipment().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
            if (shipment.getStatus() == ShipmentStatus.DELIVERED || shipment.getStatus() == ShipmentStatus.RETURNED) {
                throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL);
            }
            shipment.setStatus(ShipmentStatus.FAILED);
            shipment.setProviderStatus("CANCELLED");
            shipmentRepository.save(shipment);
            ShipmentEvent event = new ShipmentEvent();
            event.setShipment(shipment);
            event.setStatus(ShipmentStatus.FAILED);
            event.setDescription("Shipment cancellation confirmed. " + note);
            event.setEventTime(LocalDateTime.now());
            eventRepository.save(event);
        }
        orderService.finalizeCancellation(operation.getOrderId(), operation.getRequestedBy(), operation.getReason());
        operation.setStatus(CancellationOperationStatus.CONFIRMED);
        operation.setLastErrorCode(null);
        operation.setCompletedAt(LocalDateTime.now());
        return operationRepository.save(operation);
    }

    private CancellationOperationResponse response(ShipmentCancellationOperation operation) {
        CancellationOrderContext order = orderService.getCancellationContext(operation.getOrderId());
        return CancellationOperationResponse.builder()
                .id(operation.getId())
                .orderId(order.id())
                .orderCode(order.orderCode())
                .shipmentId(operation.getShipment() == null ? null : operation.getShipment().getId())
                .idempotencyKey(operation.getIdempotencyKey())
                .status(operation.getStatus())
                .orderStatus(order.status())
                .lastErrorCode(operation.getLastErrorCode())
                .requestedAt(operation.getCreatedAt())
                .completedAt(operation.getCompletedAt())
                .build();
    }

    private boolean isProviderBacked(Shipment shipment) {
        return shipment.getCarrierEntity() != null
                && shipment.getCarrierEntity().getProviderType() != CarrierProviderType.MANUAL;
    }

    public record StartResult(ShipmentCancellationOperation operation, CarrierProvider provider,
                              Shipment shipment, CarrierConfig config, boolean interception, boolean existing) {}
}
