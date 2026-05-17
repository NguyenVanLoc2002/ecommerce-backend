package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.provider.*;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.AhamoveOrderPayload;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.AhamovePathPoint;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.AhamoveCreateOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AhamoveCarrierProvider implements CarrierProvider {

    private final AhamoveClient client;
    private final AhamoveMapper mapper;
    private final AhamoveConfigResolver configResolver;

    @Override
    public String getProviderType() {
        return CarrierProviderType.AHAMOVE.name();
    }

    @Override
    public ShippingRateResult calculateRate(ShippingRateRequest request, CarrierConfig config) {
        if (request == null || request.order() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "ShippingRateRequest.order is required for AhaMove");
        }
        AhamoveResolvedConfig resolvedConfig = configResolver.resolve(config);
        return mapper.toRateResult(client.estimateFee(
                mapper.toEstimateRequest(request.order(), resolvedConfig),
                resolvedConfig));
    }

    @Override
    public ShippingOrderResult createShipment(Shipment shipment, Order order, CarrierConfig config) {
        AhamoveResolvedConfig resolvedConfig = configResolver.resolve(config);
        AhamoveCreateOrderResponse response = client.createOrder(
                mapper.toCreateOrderRequest(shipment, order, resolvedConfig),
                resolvedConfig);
        return mapper.toOrderResult(response, shipment);
    }

    @Override
    public boolean cancelShipment(Shipment shipment, String reason, CarrierConfig config) {
        AhamoveResolvedConfig resolvedConfig = configResolver.resolve(config);
        return client.cancelOrder(
                shipment.getCarrierShipmentId(),
                shipment.getTrackingNumber(),
                reason,
                resolvedConfig);
    }

    @Override
    public ShipmentTrackingResult getTracking(Shipment shipment, CarrierConfig config) {
        AhamoveResolvedConfig resolvedConfig = configResolver.resolve(config);
        if (shipment.getCarrierShipmentId() == null || shipment.getCarrierShipmentId().isBlank()) {
            throw new AppException(ErrorCode.SHIPMENT_PROVIDER_ORDER_NOT_FOUND,
                    "AhaMove provider order id is missing on shipment");
        }
        AhamoveOrderPayload payload = client.getOrderInfo(shipment.getCarrierShipmentId(), resolvedConfig);
        String trackingUrl = null;
        try {
            trackingUrl = client.getTrackingLink(shipment.getCarrierShipmentId(), resolvedConfig);
        } catch (AppException ex) {
            log.warn("AhaMove tracking link lookup failed for shipmentId={} providerOrderId={} error={}",
                    shipment.getId(), shipment.getCarrierShipmentId(), ex.getMessage());
        }
        ShipmentStatus status = mapPayloadStatus(payload);
        return mapper.toTrackingResult(
                shipment,
                payload,
                trackingUrl,
                status,
                buildDescription(payload, status),
                extractLocation(payload, status),
                extractEventTime(payload, status));
    }

    @Override
    public ShipmentStatus mapStatus(String carrierStatus) {
        String normalized = normalizeStatus(carrierStatus);
        if (normalized == null) {
            return ShipmentStatus.PENDING;
        }
        return switch (normalized) {
            case "IDLE", "ASSIGNING" -> ShipmentStatus.PENDING;
            case "ACCEPTED", "BOARDED" -> ShipmentStatus.PICKING;
            case "IN_PROCESS" -> ShipmentStatus.IN_TRANSIT;
            case "COMPLETING" -> ShipmentStatus.OUT_FOR_DELIVERY;
            case "COMPLETED" -> ShipmentStatus.DELIVERED;
            case "CANCELLED", "FAILED" -> ShipmentStatus.FAILED;
            case "RETURNED", "IN_RETURN" -> ShipmentStatus.RETURNED;
            default -> {
                log.warn("Unknown AhaMove status received: status={}", carrierStatus);
                yield ShipmentStatus.PENDING;
            }
        };
    }

    ShipmentStatus mapPayloadStatus(AhamoveOrderPayload payload) {
        if (payload == null) {
            return ShipmentStatus.PENDING;
        }
        String normalized = normalizeStatus(payload.getStatus());
        if ("COMPLETED".equals(normalized)) {
            String subStatus = normalizeStatus(payload.getSubStatus());
            AhamovePathPointSelection selection = selectDropoffPath(payload.getPath());
            String pathStatus = selection.point() != null ? normalizeStatus(selection.point().getStatus()) : null;
            if ("RETURNED".equals(subStatus) || "IN_RETURN".equals(subStatus)) {
                return ShipmentStatus.RETURNED;
            }
            if ("FAILED".equals(pathStatus)) {
                return ShipmentStatus.FAILED;
            }
            return ShipmentStatus.DELIVERED;
        }
        if ("IN_PROCESS".equals(normalized) && "COMPLETING".equals(normalizeStatus(payload.getSubStatus()))) {
            return ShipmentStatus.OUT_FOR_DELIVERY;
        }
        return mapStatus(payload.getStatus());
    }

    String buildDescription(AhamoveOrderPayload payload, ShipmentStatus status) {
        String comment = null;
        AhamovePathPointSelection selection = selectDropoffPath(payload.getPath());
        if (selection.point() != null) {
            comment = selection.point().getFailComment();
        }
        if ((comment == null || comment.isBlank()) && payload.getCancelComment() != null) {
            comment = payload.getCancelComment();
        }
        String suffix = comment != null && !comment.isBlank() ? " " + comment.trim() : "";
        return switch (status) {
            case PENDING -> "AhaMove is assigning a driver." + suffix;
            case PICKING -> "AhaMove driver accepted the shipment." + suffix;
            case IN_TRANSIT -> "AhaMove driver picked up the shipment." + suffix;
            case OUT_FOR_DELIVERY -> "AhaMove driver is approaching the delivery point." + suffix;
            case DELIVERED -> "AhaMove delivered the shipment." + suffix;
            case FAILED -> "AhaMove reported the shipment as failed or cancelled." + suffix;
            case RETURNED -> "AhaMove returned the shipment to the sender." + suffix;
        };
    }

    String extractLocation(AhamoveOrderPayload payload, ShipmentStatus status) {
        if (payload == null) {
            return null;
        }
        List<AhamovePathPoint> path = payload.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (status == ShipmentStatus.PENDING || status == ShipmentStatus.PICKING) {
            return firstNonBlank(path.get(0).getShortAddress(), path.get(0).getAddress());
        }
        AhamovePathPointSelection selection = selectDropoffPath(path);
        if (selection.point() != null) {
            return firstNonBlank(selection.point().getShortAddress(), selection.point().getAddress());
        }
        return firstNonBlank(path.get(path.size() - 1).getShortAddress(), path.get(path.size() - 1).getAddress());
    }

    LocalDateTime extractEventTime(AhamoveOrderPayload payload, ShipmentStatus status) {
        if (payload == null) {
            return LocalDateTime.now();
        }
        Double epochSeconds = switch (status) {
            case PICKING -> firstNonNull(payload.getBoardTime(), payload.getAcceptTime());
            case IN_TRANSIT, OUT_FOR_DELIVERY -> payload.getPickupTime();
            case DELIVERED -> firstNonNull(selectDropoffPath(payload.getPath()).completeTime(), payload.getCompleteTime());
            case FAILED -> firstNonNull(selectDropoffPath(payload.getPath()).failTime(),
                    firstNonNull(payload.getCancelTime(), payload.getCompleteTime()));
            case RETURNED -> firstNonNull(selectDropoffPath(payload.getPath()).returnTime(), payload.getCompleteTime());
            default -> null;
        };
        if (epochSeconds == null || epochSeconds <= 0) {
            return LocalDateTime.now();
        }
        long seconds = epochSeconds.longValue();
        long nanos = Math.round((epochSeconds - seconds) * 1_000_000_000L);
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneOffset.UTC);
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private Double firstNonNull(Double first, Double second) {
        return first != null && first > 0 ? first : second;
    }

    private AhamovePathPointSelection selectDropoffPath(List<AhamovePathPoint> path) {
        if (path == null || path.size() < 2) {
            return new AhamovePathPointSelection(null, null, null, null);
        }
        var point = path.get(path.size() - 1);
        return new AhamovePathPointSelection(point, point.getCompleteTime(), point.getFailTime(), point.getReturnTime());
    }

    private record AhamovePathPointSelection(
            AhamovePathPoint point,
            Double completeTime,
            Double failTime,
            Double returnTime
    ) {}
}
