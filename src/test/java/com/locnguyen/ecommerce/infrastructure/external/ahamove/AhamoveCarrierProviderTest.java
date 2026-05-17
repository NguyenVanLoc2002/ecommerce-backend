package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingRateRequest;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingRateResult;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.AhamoveEstimateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AhamoveCarrierProviderTest {

    @Mock AhamoveClient client;
    @Mock AhamoveMapper mapper;
    @Mock AhamoveConfigResolver configResolver;

    @InjectMocks AhamoveCarrierProvider provider;

    @Test
    void mapStatus_mapsKnownStatuses() {
        assertThat(provider.mapStatus("IDLE")).isEqualTo(ShipmentStatus.PENDING);
        assertThat(provider.mapStatus("ASSIGNING")).isEqualTo(ShipmentStatus.PENDING);
        assertThat(provider.mapStatus("ACCEPTED")).isEqualTo(ShipmentStatus.PICKING);
        assertThat(provider.mapStatus("IN PROCESS")).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(provider.mapStatus("COMPLETING")).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
        assertThat(provider.mapStatus("COMPLETED")).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(provider.mapStatus("CANCELLED")).isEqualTo(ShipmentStatus.FAILED);
        assertThat(provider.mapStatus("FAILED")).isEqualTo(ShipmentStatus.FAILED);
    }

    @Test
    void mapStatus_unknownStatus_returnsPending() {
        assertThat(provider.mapStatus("MYSTERY")).isEqualTo(ShipmentStatus.PENDING);
    }

    @Test
    void calculateRate_missingConfig_throws() {
        CarrierConfig config = new CarrierConfig();
        when(configResolver.resolve(config))
                .thenThrow(new AppException(ErrorCode.CARRIER_CONFIG_MISSING));

        assertThatThrownBy(() -> provider.createShipment(new Shipment(), new Order(), config))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CARRIER_CONFIG_MISSING);
    }

    @Test
    void createShipment_disabledConfig_throws() {
        CarrierConfig config = new CarrierConfig();
        when(configResolver.resolve(config))
                .thenThrow(new AppException(ErrorCode.CARRIER_CONFIG_DISABLED));

        assertThatThrownBy(() -> provider.createShipment(new Shipment(), new Order(), config))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CARRIER_CONFIG_DISABLED);
    }

    @Test
    void calculateRate_usesOrderIdAndResolvedConfig() {
        CarrierConfig config = new CarrierConfig();
        Order order = new Order();
        AhamoveResolvedConfig resolvedConfig = new AhamoveResolvedConfig(
                "https://partner-apistg.ahamove.com", "api-key", "84338710667",
                "Locen Studio", "token", "pickup", "pickup", "Locen Studio", "84338710667",
                null, null, "BIKE", "CASH", java.util.List.of());

        when(configResolver.resolve(config)).thenReturn(resolvedConfig);
        when(mapper.toEstimateRequest(order, resolvedConfig)).thenReturn(AhamoveEstimateRequest.builder().build());
        when(client.estimateFee(any(), any())).thenReturn(java.util.List.of(new com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.AhamoveEstimateOptionResponse()));
        when(mapper.toRateResult(any())).thenReturn(
                ShippingRateResult.builder()
                        .fee(java.math.BigDecimal.TEN)
                        .currency("VND")
                        .serviceName("BIKE")
                        .build());

        var result = provider.calculateRate(
                ShippingRateRequest.builder()
                        .order(order)
                        .build(),
                config);

        assertThat(result.fee()).isEqualByComparingTo("10");
    }
}
