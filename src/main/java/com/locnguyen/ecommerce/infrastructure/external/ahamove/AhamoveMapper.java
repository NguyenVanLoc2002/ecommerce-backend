package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.provider.ShipmentTrackingResult;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingOrderResult;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingRateResult;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.entity.OrderItem;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class AhamoveMapper {

    public AhamoveEstimateRequest toEstimateRequest(Order order, AhamoveResolvedConfig config) {
        validateOrder(order);
        validatePickup(config);
        return AhamoveEstimateRequest.builder()
                .orderTime(0L)
                .path(buildPath(order, null, config))
                .groupServices(List.of(AhamoveEstimateRequest.AhamoveGroupService.builder()
                        .id(requireNonBlank(config.groupServiceId(), "AhaMove groupServiceId must be configured"))
                        .groupRequests(buildGroupRequests(config))
                        .build()))
                .paymentMethod(resolvePaymentMethod(order, config))
                .remarks(buildRemarks(order, null))
                .items(buildItems(order))
                .packageDetail(buildPackageDetails(order))
                .build();
    }

    public AhamoveCreateOrderRequest toCreateOrderRequest(
            Shipment shipment,
            Order order,
            AhamoveResolvedConfig config) {
        validateOrder(order);
        validatePickup(config);
        return AhamoveCreateOrderRequest.builder()
                .orderTime(0L)
                .path(buildPath(order, shipment, config))
                .groupServiceId(requireNonBlank(config.groupServiceId(), "AhaMove groupServiceId must be configured"))
                .groupRequests(buildGroupRequests(config))
                .paymentMethod(resolvePaymentMethod(order, config))
                .remarks(buildRemarks(order, shipment))
                .items(buildItems(order))
                .packageDetail(buildPackageDetails(order))
                .build();
    }

    public ShippingRateResult toRateResult(List<AhamoveEstimateOptionResponse> options) {
        if (options == null || options.isEmpty()) {
            throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                    "AhaMove estimate fee returned no service options");
        }
        AhamoveEstimateOptionResponse selected = options.stream()
                .filter(option -> option.getData() != null)
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                        "AhaMove estimate fee returned no priced option"));
        BigDecimal fee = firstNonNull(selected.getData().getTotalFee(), selected.getData().getTotalPrice());
        if (fee == null) {
            throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                    "AhaMove estimate fee did not include a total fee");
        }
        return ShippingRateResult.builder()
                .fee(fee)
                .currency("VND")
                .serviceName(selected.getServiceId())
                .build();
    }

    public ShippingOrderResult toOrderResult(AhamoveCreateOrderResponse response, Shipment shipment) {
        String trackingNumber = shipment.getTrackingNumber();
        if ((trackingNumber == null || trackingNumber.isBlank())
                && response.getOrder() != null
                && response.getOrder().getPath() != null
                && response.getOrder().getPath().size() > 1) {
            trackingNumber = response.getOrder().getPath().get(response.getOrder().getPath().size() - 1)
                    .getTrackingNumber();
        }
        return ShippingOrderResult.builder()
                .carrierShipmentId(response.getOrderId())
                .trackingNumber(trackingNumber)
                .trackingUrl(response.getSharedLink())
                .rawStatus(response.getStatus())
                .build();
    }

    public ShipmentTrackingResult toTrackingResult(
            Shipment shipment,
            AhamoveOrderPayload payload,
            String trackingUrl,
            ShipmentStatus status,
            String description,
            String location,
            java.time.LocalDateTime eventTime) {
        String trackingNumber = shipment.getTrackingNumber();
        if ((trackingNumber == null || trackingNumber.isBlank())
                && payload.getPath() != null
                && payload.getPath().size() > 1) {
            trackingNumber = payload.getPath().get(payload.getPath().size() - 1).getTrackingNumber();
        }
        return ShipmentTrackingResult.builder()
                .trackingNumber(trackingNumber)
                .trackingUrl(trackingUrl)
                .status(status)
                .rawStatus(payload.getStatus())
                .description(description)
                .location(location)
                .eventTime(eventTime)
                .build();
    }

    private List<AhamovePathPoint> buildPath(Order order, Shipment shipment, AhamoveResolvedConfig config) {
        List<AhamovePathPoint> path = new ArrayList<>();
        path.add(AhamovePathPoint.builder()
                .lat(config.pickupLat())
                .lng(config.pickupLng())
                .address(config.pickupAddress())
                .shortAddress(config.pickupShortAddress())
                .name(firstNonBlank(config.pickupName(), config.brandName()))
                .mobile(requireNonBlank(config.pickupPhone(), "AhaMove pickup phone is required"))
                .remarks(firstNonBlank(config.brandName(), "Store") + " pickup for order " + order.getOrderCode())
                .build());
        path.add(AhamovePathPoint.builder()
                .address(buildShippingAddress(order))
                .shortAddress(buildShortAddress(order))
                .name(requireNonBlank(order.getReceiverName(), "Receiver name is required"))
                .mobile(requireNonBlank(order.getReceiverPhone(), "Receiver phone is required"))
                .cod(resolveCodAmount(order))
                .itemValue(order.getSubTotal())
                .trackingNumber(shipment != null ? shipment.getShipmentCode() : order.getOrderCode())
                .remarks(buildRemarks(order, shipment))
                .build());
        return path;
    }

    private List<AhamoveRequestOption> buildGroupRequests(AhamoveResolvedConfig config) {
        if (config.groupRequests() == null || config.groupRequests().isEmpty()) {
            return List.of();
        }
        return config.groupRequests().stream()
                .map(option -> AhamoveRequestOption.builder()
                        .id(option.getId())
                        .num(option.getNum())
                        .tierCode(option.getTierCode())
                        .build())
                .toList();
    }

    private List<AhamoveItem> buildItems(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new AppException(ErrorCode.ORDER_EMPTY);
        }
        List<AhamoveItem> items = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            items.add(AhamoveItem.builder()
                    .id(item.getSku())
                    .num(item.getQuantity())
                    .name(item.getProductName() + " - " + item.getVariantName())
                    .price(firstNonNull(item.getSalePrice(), item.getUnitPrice()))
                    .build());
        }
        return items;
    }

    private List<AhamovePackageDetail> buildPackageDetails(Order order) {
        BigDecimal weightKg = calculateWeightKg(order);
        String description = order.getItems().stream()
                .map(OrderItem::getProductName)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Fashion items");
        if (weightKg == null) {
            return List.of(AhamovePackageDetail.builder()
                    .description(description)
                    .build());
        }
        return List.of(AhamovePackageDetail.builder()
                .weight(weightKg)
                .description(description)
                .build());
    }

    private BigDecimal calculateWeightKg(Order order) {
        BigDecimal totalGram = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            Integer weightGram = item.getVariant() != null ? item.getVariant().getWeightGram() : null;
            if (weightGram != null && weightGram > 0 && item.getQuantity() != null) {
                totalGram = totalGram.add(BigDecimal.valueOf(weightGram.longValue() * item.getQuantity()));
            }
        }
        if (totalGram.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalGram.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private BigDecimal resolveCodAmount(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.COD) {
            return BigDecimal.ZERO;
        }
        BigDecimal codAmount = firstNonNull(order.getTotalAmount(), order.getSubTotal());
        if (codAmount == null || codAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "COD amount must not be negative");
        }
        return codAmount;
    }

    private String resolvePaymentMethod(Order order, AhamoveResolvedConfig config) {
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            return "CASH";
        }
        return firstNonBlank(config.paymentMethod(), "CASH");
    }

    private String buildRemarks(Order order, Shipment shipment) {
        StringBuilder builder = new StringBuilder("Order ").append(order.getOrderCode());
        if (shipment != null && shipment.getNote() != null && !shipment.getNote().isBlank()) {
            builder.append(" | ").append(shipment.getNote().trim());
        }
        if (order.getCustomerNote() != null && !order.getCustomerNote().isBlank()) {
            builder.append(" | ").append(order.getCustomerNote().trim());
        }
        return builder.toString();
    }

    private String buildShippingAddress(Order order) {
        List<String> parts = List.of(order.getShippingStreet(), order.getShippingWard(),
                order.getShippingDistrict(), order.getShippingCity());
        String address = parts.stream()
                .filter(part -> part != null && !part.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        return requireNonBlank(address, "Shipping address is required");
    }

    private String buildShortAddress(Order order) {
        return firstNonBlank(order.getShippingDistrict(), order.getShippingCity());
    }

    private void validateOrder(Order order) {
        if (order == null) {
            throw new AppException(ErrorCode.ORDER_NOT_FOUND);
        }
        requireNonBlank(order.getReceiverPhone(), "Receiver phone is required");
        buildShippingAddress(order);
    }

    private void validatePickup(AhamoveResolvedConfig config) {
        requireNonBlank(config.pickupAddress(), "AhaMove pickupAddress is required");
        requireNonBlank(config.pickupPhone(), "AhaMove pickupPhone is required");
        requireNonBlank(config.groupServiceId(), "AhaMove groupServiceId is required");
    }

    private String requireNonBlank(String value, String message) {
        String normalized = firstNonBlank(value);
        if (normalized == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
