package com.locnguyen.ecommerce.domains.shipment.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProvider;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderRegistry;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingOrderResult;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierRepository;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.shipment.dto.CreateShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentBookingAttempt;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentEvent;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentResponse;
import com.locnguyen.ecommerce.domains.shipment.mapper.ShipmentMapper;
import com.locnguyen.ecommerce.domains.shipment.enums.BookingAttemptStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.CarrierOverrideReason;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentBookingAttemptRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentEventRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentBookingCoordinator {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final ShipmentBookingAttemptRepository attemptRepository;
    private final OrderRepository orderRepository;
    private final CarrierRepository carrierRepository;
    private final CarrierConfigRepository carrierConfigRepository;
    private final CarrierProviderRegistry providerRegistry;
    private final AuditLogService auditLogService;
    private final ShipmentMapper shipmentMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PreparedBooking prepare(CreateShipmentRequest request) {
        Order order = orderRepository.findByIdWithItemsAndLock(request.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID,
                    "Order must be in PROCESSING status to create a shipment. Current: " + order.getStatus());
        }
        ensureOnlinePaymentReceived(order);
        if (shipmentRepository.existsByOrderId(order.getId())) {
            throw new AppException(ErrorCode.SHIPMENT_ALREADY_EXISTS);
        }

        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShipmentCode(CodeGenerator.generateShipmentCode());
        shipment.setEstimatedDeliveryDate(request.getEstimatedDeliveryDate());
        shipment.setShippingFee(request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO);
        shipment.setEstimatedCarrierCost(request.getEstimatedCarrierCost());
        shipment.setNote(normalizeOptional(request.getNote()));
        shipment.setTrackingNumber(normalizeOptional(request.getTrackingNumber()));

        UUID selectedCarrierId = request.isUseManualCarrier()
                ? null : request.getCarrierId() != null ? request.getCarrierId() : order.getCarrierId();
        String selectedCarrierName = request.getCarrier() != null ? request.getCarrier() : order.getCarrierName();
        String checkoutCarrierName = normalizeOptional(order.getCarrierName());
        boolean carrierOverride = order.getCarrierId() != null
                ? !order.getCarrierId().equals(selectedCarrierId)
                : checkoutCarrierName != null && (selectedCarrierId != null
                || !checkoutCarrierName.equals(normalizeOptional(selectedCarrierName)));
        if (carrierOverride) {
            if (!SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
                throw new AppException(ErrorCode.FORBIDDEN, "Carrier override requires ADMIN or SUPER_ADMIN permission");
            }
            CarrierOverrideReason reason = request.getOverrideReasonCode();
            if (reason == null || (reason == CarrierOverrideReason.OTHER
                    && (request.getOverrideNote() == null || request.getOverrideNote().isBlank()))) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "A controlled override reason is required; OTHER also requires a note");
            }
            shipment.setOverrideReasonCode(reason.name());
            shipment.setOverrideNote(normalizeOptional(request.getOverrideNote()));
            shipment.setOverrideActor(SecurityUtils.getCurrentUsernameOrSystem());
            shipment.setOverrideFromCarrierId(order.getCarrierId());
            shipment.setOverrideToCarrierId(selectedCarrierId);
            shipment.setOverrideAt(LocalDateTime.now());
        }
        CarrierProvider provider = null;
        CarrierConfig config = null;
        CarrierProviderType providerType = CarrierProviderType.MANUAL;
        if (selectedCarrierId == null) {
            shipment.setCarrier(normalizeRequiredCarrierName(selectedCarrierName));
            shipment.setBookingStatus(BookingAttemptStatus.INTERNAL_ONLY);
        } else {
            Carrier carrier = carrierRepository.findById(selectedCarrierId)
                    .orElseThrow(() -> new AppException(ErrorCode.CARRIER_NOT_FOUND));
            if (carrier.getStatus() != CarrierStatus.ACTIVE) {
                throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                        "Carrier is inactive and cannot be assigned to shipments");
            }
            shipment.setCarrierEntity(carrier);
            shipment.setCarrier(carrier.getName());
            providerType = carrier.getProviderType();
            provider = providerRegistry.find(providerType.name())
                    .orElseThrow(() -> new AppException(ErrorCode.CARRIER_PROVIDER_NOT_SUPPORTED));
            config = carrierConfigRepository.findByCarrierId(carrier.getId()).orElse(null);
            if (requiresEnabledConfig(providerType) && (config == null || !config.isEnabled())) {
                throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                        "Carrier config is missing or disabled for " + carrier.getCode());
            }
        }

        shipment = shipmentRepository.saveAndFlush(shipment);
        if (carrierOverride) {
            auditLogService.logWithActor(AuditAction.SHIPMENT_CARRIER_OVERRIDDEN, "SHIPMENT",
                    shipment.getId().toString(), shipment.getOverrideActor(),
                    "orderId=" + order.getId() + "; previousCarrierId=" + order.getCarrierId()
                            + "; previousCarrierName=" + normalizeOptional(order.getCarrierName())
                            + "; selectedCarrierId=" + selectedCarrierId + "; reason="
                            + shipment.getOverrideReasonCode() + "; selectedCarrier=" + shipment.getCarrier());
        }
        if (provider == null || providerType == CarrierProviderType.MANUAL) {
            shipment.setBookingStatus(BookingAttemptStatus.INTERNAL_ONLY);
            shipmentRepository.save(shipment);
            recordEvent(shipment, "Manual shipment created. Fulfillment is managed internally.");
            auditLogService.logWithActor(AuditAction.SHIPMENT_CREATED, "SHIPMENT", shipment.getId().toString(),
                    SecurityUtils.getCurrentUsernameOrSystem(), "source=MANUAL; status=PENDING");

            return new PreparedBooking(shipment.getId(), null, null, null, null, shipment);
        }

        String idempotencyKey = UUID.randomUUID().toString();
        ShipmentBookingAttempt attempt = new ShipmentBookingAttempt();
        attempt.setShipment(shipment);
        attempt.setProviderType(providerType);
        attempt.setIdempotencyKey(idempotencyKey);
        attempt.setRequestFingerprint(fingerprint(order.getId() + "|" + shipment.getShipmentCode()
                + "|" + providerType.name() + "|" + selectedCarrierId + "|" + shipment.getShippingFee()
                + "|" + shipment.getEstimatedDeliveryDate() + "|" + shipment.getEstimatedCarrierCost()));
        attempt.setStatus(BookingAttemptStatus.PENDING);
        attempt.setStartedAt(LocalDateTime.now());
        attemptRepository.saveAndFlush(attempt);
        shipment.setBookingAttemptKey(idempotencyKey);
        shipment.setBookingStatus(BookingAttemptStatus.PENDING);
        shipmentRepository.saveAndFlush(shipment);
        auditLogService.log(AuditAction.SHIPMENT_CREATED, "SHIPMENT", shipment.getId().toString(),
                "Carrier booking attempt persisted before provider call; provider=" + providerType);
        return new PreparedBooking(shipment.getId(), attempt.getId(), provider, config, idempotencyKey, shipment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID attemptId, ShippingOrderResult result) {
        ShipmentBookingAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        Shipment shipment = attempt.getShipment();
        if (attempt.getStatus() == BookingAttemptStatus.SUCCEEDED) return;
        applyProviderResult(shipment, result);
        attempt.setStatus(BookingAttemptStatus.SUCCEEDED);
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setLastErrorCode(null);
        shipment.setBookingStatus(BookingAttemptStatus.SUCCEEDED);
        shipmentRepository.save(shipment);
        attemptRepository.save(attempt);
        recordEvent(shipment, "Carrier booking confirmed.");

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnknown(UUID attemptId, Throwable failure) {
        ShipmentBookingAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        if (attempt.getStatus() == BookingAttemptStatus.SUCCEEDED) return;
        String code = failure instanceof AppException appException
                ? appException.getErrorCode().getCode() : "PROVIDER_OUTCOME_UNKNOWN";
        attempt.setStatus(BookingAttemptStatus.UNKNOWN);
        attempt.setLastErrorCode(code);
        attempt.setCompletedAt(null);
        attempt.getShipment().setBookingStatus(BookingAttemptStatus.UNKNOWN);
        attemptRepository.save(attempt);
        shipmentRepository.save(attempt.getShipment());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startSafeRetry(UUID attemptId) {
        ShipmentBookingAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        if (attempt.getStatus() == BookingAttemptStatus.SUCCEEDED) return;
        attempt.setAttemptCount(attempt.getAttemptCount() + 1);
        attempt.setStatus(BookingAttemptStatus.PENDING);
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setCompletedAt(null);
        attempt.setLastErrorCode(null);
        attempt.getShipment().setBookingStatus(BookingAttemptStatus.PENDING);
        attemptRepository.save(attempt);
        shipmentRepository.save(attempt.getShipment());
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentResponse(UUID id) {
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        return shipmentMapper.toResponse(shipment);
    }

    @Transactional(readOnly = true)
    public BookingLookup getBookingLookup(UUID shipmentId) {
        ShipmentBookingAttempt attempt = attemptRepository.findByShipmentId(shipmentId).orElse(null);
        if (attempt == null) return null;
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND));
        Order order = orderRepository.findWithItemsById(shipment.getOrder().getId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        shipment.setOrder(order);
        Carrier carrier = shipment.getCarrierEntity();
        if (carrier == null) return null;
        CarrierProvider provider = providerRegistry.find(carrier.getProviderType().name())
                .orElseThrow(() -> new AppException(ErrorCode.CARRIER_PROVIDER_NOT_SUPPORTED));
        CarrierConfig config = carrierConfigRepository.findByCarrierId(carrier.getId()).orElse(null);
        if (requiresEnabledConfig(carrier.getProviderType()) && (config == null || !config.isEnabled())) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING);
        }
        return new BookingLookup(attempt.getId(), attempt.getStatus(), attempt.getIdempotencyKey(),
                shipment.getShipmentCode(), provider, config, shipment);
    }

    private void ensureOnlinePaymentReceived(Order order) {
        if (order.getPaymentMethod() == PaymentMethod.ONLINE && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID, "Cannot create shipment for online order: payment not received");
        }
    }

    private void recordEvent(Shipment shipment, String description) {
        ShipmentEvent event = new ShipmentEvent();
        event.setShipment(shipment);
        event.setStatus(ShipmentStatus.PENDING);
        event.setDescription(description);
        event.setEventTime(LocalDateTime.now());
        shipmentEventRepository.save(event);
    }

    private void applyProviderResult(Shipment shipment, ShippingOrderResult result) {
        if (result == null) throw new AppException(ErrorCode.CARRIER_PROVIDER_STATUS_UNKNOWN);
        if (hasText(result.carrierShipmentId())) shipment.setCarrierShipmentId(result.carrierShipmentId().trim());
        if (hasText(result.trackingNumber())) shipment.setTrackingNumber(result.trackingNumber().trim());
        if (hasText(result.trackingUrl())) shipment.setProviderTrackingUrl(result.trackingUrl().trim());
        if (hasText(result.rawStatus())) shipment.setProviderStatus(result.rawStatus().trim());
    }

    private boolean requiresEnabledConfig(CarrierProviderType providerType) {
        return providerType != CarrierProviderType.MANUAL && providerType != CarrierProviderType.MOCK;
    }

    private String normalizeRequiredCarrierName(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new AppException(ErrorCode.VALIDATION_ERROR, "carrier must not be blank");
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record PreparedBooking(UUID shipmentId, UUID attemptId, CarrierProvider provider,
                                  CarrierConfig config, String idempotencyKey, Shipment shipment) {}

    public record BookingLookup(UUID attemptId, BookingAttemptStatus status, String idempotencyKey,
                                String shipmentCode, CarrierProvider provider, CarrierConfig config,
                                Shipment shipment) {}
}
