package com.locnguyen.ecommerce.domains.shipment.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.CodeGenerator;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProvider;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderRegistry;
import com.locnguyen.ecommerce.domains.carrier.provider.ShipmentTrackingResult;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingOrderResult;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierRepository;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.shipment.dto.CancelProviderShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.CreateShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentFilter;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentResponse;
import com.locnguyen.ecommerce.domains.shipment.dto.UpdateShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.UpdateShipmentStatusRequest;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentEvent;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.shipment.mapper.ShipmentMapper;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentEventRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentService;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentProviderUpdate;
import com.locnguyen.ecommerce.domains.shipment.specification.ShipmentSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final OrderRepository orderRepository;
    private final ShipmentMapper shipmentMapper;
    private final CarrierRepository carrierRepository;
    private final CarrierConfigRepository carrierConfigRepository;
    private final CarrierProviderRegistry carrierProviderRegistry;

    @Override
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
        shipment.setEstimatedDeliveryDate(request.getEstimatedDeliveryDate());
        shipment.setShippingFee(
                request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO);
        shipment.setNote(normalizeOptional(request.getNote()));
        shipment.setTrackingNumber(normalizeOptional(request.getTrackingNumber()));

        UUID selectedCarrierId = request.getCarrierId() != null ? request.getCarrierId() : order.getCarrierId();
        String selectedCarrierName = request.getCarrier() != null ? request.getCarrier() : order.getCarrierName();
        configureCarrier(shipment, order, selectedCarrierId, selectedCarrierName, true);

        shipment = shipmentRepository.save(shipment);

        recordEvent(shipment, ShipmentStatus.PENDING, null,
                "Shipment created. Awaiting carrier pickup.", LocalDateTime.now());

        transitionOrderStatus(order, OrderStatus.SHIPPED);

        log.info("Shipment created: code={} orderId={} carrier={} linkedCarrierId={}",
                shipment.getShipmentCode(),
                order.getId(),
                shipment.getCarrier(),
                shipment.getCarrierEntity() != null ? shipment.getCarrierEntity().getId() : null);
        return shipmentMapper.toResponse(findByIdOrThrow(shipment.getId()));
    }

    @Override
    @Transactional
    public ShipmentResponse updateShipment(UUID shipmentId, UpdateShipmentRequest request) {
        Shipment shipment = findByIdOrThrow(shipmentId);

        if (request.getCarrierId() != null || request.getCarrier() != null) {
            configureCarrier(shipment, shipment.getOrder(), request.getCarrierId(), request.getCarrier(), false);
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
        return shipmentMapper.toResponse(shipment);
    }

    @Override
    @Transactional
    public ShipmentResponse syncProviderTracking(UUID shipmentId) {
        Shipment shipment = findByIdOrThrow(shipmentId);
        CarrierProviderContext context = resolveProviderContext(shipment);
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
    public ShipmentResponse cancelProviderShipment(UUID shipmentId, CancelProviderShipmentRequest request) {
        Shipment shipment = findByIdOrThrow(shipmentId);
        CarrierProviderContext context = resolveProviderContext(shipment);
        boolean cancelled = context.provider().cancelShipment(shipment, request.getReason(), context.config());
        if (!cancelled) {
            throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                    "Carrier provider did not confirm shipment cancellation");
        }
        return applyProviderUpdate(ShipmentProviderUpdate.builder()
                .shipmentId(shipmentId)
                .status(ShipmentStatus.FAILED)
                .providerStatus("CANCELLED")
                .trackingNumber(shipment.getTrackingNumber())
                .trackingUrl(shipment.getProviderTrackingUrl())
                .description(request.getReason())
                .eventTime(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
    public ShipmentResponse applyProviderUpdate(ShipmentProviderUpdate update) {
        Shipment shipment = findByIdOrThrow(update.shipmentId());

        if (update.providerStatus() != null && !update.providerStatus().isBlank()) {
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

        if (nextStatus != null && shouldTransition(shipment, nextStatus)) {
            shipment.setStatus(nextStatus);
            if (nextStatus == ShipmentStatus.DELIVERED) {
                shipment.setDeliveredAt(eventTime);
                transitionOrderStatus(shipment.getOrder(), OrderStatus.DELIVERED);
            }
        } else if (nextStatus != null && shipment.getStatus() != nextStatus) {
            log.warn("Skipping backward or invalid carrier status transition: shipmentId={} current={} incoming={}",
                    shipment.getId(), shipment.getStatus(), nextStatus);
        }

        shipmentRepository.save(shipment);

        if (nextStatus != null && description != null) {
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

    private void transitionOrderStatus(Order order, OrderStatus target) {
        if (!order.getStatus().canTransitionTo(target)) {
            log.warn("Skipping order status transition {} -> {} for order {}",
                    order.getStatus(), target, order.getOrderCode());
            return;
        }
        order.setStatus(target);
        orderRepository.save(order);
        log.info("Order status transitioned: orderCode={} -> {}", order.getOrderCode(), target);
    }

    private void configureCarrier(Shipment shipment, Order order, UUID carrierId,
                                  String carrierName, boolean createViaProvider) {
        if (carrierId == null) {
            if (normalizeOptional(carrierName) == null) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "carrierId or carrier must be provided, or the order must have a selected carrier");
            }
            shipment.setCarrier(normalizeRequiredCarrierName(carrierName));
            shipment.setCarrierEntity(null);
            shipment.setCarrierShipmentId(null);
            shipment.setProviderStatus(null);
            shipment.setProviderTrackingUrl(null);
            return;
        }

        Carrier carrier = carrierRepository.findById(carrierId)
                .orElseThrow(() -> new AppException(ErrorCode.CARRIER_NOT_FOUND));
        if (carrier.getStatus() != CarrierStatus.ACTIVE) {
            throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                    "Carrier is inactive and cannot be assigned to shipments");
        }

        shipment.setCarrierEntity(carrier);
        shipment.setCarrier(carrier.getName());

        CarrierProviderContext context = resolveProviderContext(carrier);

        if (!createViaProvider) {
            return;
        }

        ShippingOrderResult result;
        try {
            result = context.provider().createShipment(shipment, order, context.config());
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                    "Failed to create shipment with carrier " + carrier.getCode());
        }

        if (result == null) {
            return;
        }
        if (result.carrierShipmentId() != null && !result.carrierShipmentId().isBlank()) {
            shipment.setCarrierShipmentId(result.carrierShipmentId());
        }
        if (result.trackingNumber() != null && !result.trackingNumber().isBlank()) {
            shipment.setTrackingNumber(result.trackingNumber());
        }
        if (result.trackingUrl() != null && !result.trackingUrl().isBlank()) {
            shipment.setProviderTrackingUrl(result.trackingUrl());
        }
        if (result.rawStatus() != null && !result.rawStatus().isBlank()) {
            shipment.setProviderStatus(result.rawStatus());
        }
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
        return shipment.getStatus().canTransitionTo(nextStatus);
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

    private String normalizeRequiredCarrierName(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "carrier must not be blank");
        }
        return normalized;
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
