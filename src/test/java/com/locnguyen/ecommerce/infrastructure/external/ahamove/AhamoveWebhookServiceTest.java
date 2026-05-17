package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierRepository;
import com.locnguyen.ecommerce.domains.shipment.entity.CarrierWebhookLog;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.shipment.repository.CarrierWebhookLogRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentService;
import com.locnguyen.ecommerce.domains.shipment.service.impl.CarrierWebhookLogPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AhamoveWebhookServiceTest {

    @Mock CarrierRepository carrierRepository;
    @Mock CarrierConfigRepository carrierConfigRepository;
    @Mock ShipmentRepository shipmentRepository;
    @Mock ShipmentService shipmentService;
    @Mock CarrierWebhookLogRepository carrierWebhookLogRepository;
    @Mock CarrierWebhookLogPersister carrierWebhookLogPersister;
    @Mock AhamoveConfigResolver configResolver;
    @Mock AhamoveCarrierProvider carrierProvider;

    private AhamoveWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new AhamoveWebhookService(
                carrierRepository,
                carrierConfigRepository,
                shipmentRepository,
                shipmentService,
                carrierWebhookLogRepository,
                carrierWebhookLogPersister,
                configResolver,
                carrierProvider,
                new ObjectMapper()
        );
    }

    @Test
    void validWebhook_updatesShipmentStatus() {
        Carrier carrier = buildCarrier();
        CarrierConfig config = new CarrierConfig();
        Shipment shipment = buildShipment();
        CarrierWebhookLog log = new CarrierWebhookLog();
        AhamoveResolvedConfig resolvedConfig = buildResolvedConfig();

        when(carrierRepository.findFirstByProviderType(CarrierProviderType.AHAMOVE)).thenReturn(Optional.of(carrier));
        when(carrierConfigRepository.findByCarrierId(carrier.getId())).thenReturn(Optional.of(config));
        when(configResolver.resolve(config)).thenReturn(resolvedConfig);
        when(carrierWebhookLogRepository.existsByEventKey(any())).thenReturn(false);
        when(carrierWebhookLogPersister.createInitialLog(any(), any(), any(), any(), any(), any(), any())).thenReturn(log);
        when(shipmentRepository.findByCarrierShipmentId("AHA-1")).thenReturn(Optional.of(shipment));
        when(carrierProvider.mapPayloadStatus(any())).thenReturn(ShipmentStatus.DELIVERED);
        when(carrierProvider.buildDescription(any(), eq(ShipmentStatus.DELIVERED))).thenReturn("Delivered by AhaMove");
        when(carrierProvider.extractLocation(any(), eq(ShipmentStatus.DELIVERED))).thenReturn("District 1");
        when(carrierProvider.extractEventTime(any(), eq(ShipmentStatus.DELIVERED))).thenReturn(LocalDateTime.now());
        when(shipmentService.applyProviderUpdate(any())).thenReturn(null);

        webhookService.receiveWebhook(validPayload("COMPLETED"), Map.of("X-Webhook-Token", "token"));

        ArgumentCaptor<com.locnguyen.ecommerce.domains.shipment.service.ShipmentProviderUpdate> captor =
                ArgumentCaptor.forClass(com.locnguyen.ecommerce.domains.shipment.service.ShipmentProviderUpdate.class);
        verify(shipmentService).applyProviderUpdate(captor.capture());
        assertThat(captor.getValue().shipmentId()).isEqualTo(shipment.getId());
        assertThat(captor.getValue().status()).isEqualTo(ShipmentStatus.DELIVERED);
        verify(carrierWebhookLogRepository).save(log);
        assertThat(log.isProcessed()).isTrue();
    }

    @Test
    void invalidToken_rejectsWithoutDbMutation() {
        Carrier carrier = buildCarrier();
        CarrierConfig config = new CarrierConfig();
        when(carrierRepository.findFirstByProviderType(CarrierProviderType.AHAMOVE)).thenReturn(Optional.of(carrier));
        when(carrierConfigRepository.findByCarrierId(carrier.getId())).thenReturn(Optional.of(config));
        when(configResolver.resolve(config)).thenReturn(buildResolvedConfig());

        assertThatThrownBy(() -> webhookService.receiveWebhook(validPayload("ASSIGNING"), Map.of("X-Webhook-Token", "wrong")))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CARRIER_WEBHOOK_SIGNATURE_INVALID);

        verify(carrierWebhookLogPersister, never()).createInitialLog(any(), any(), any(), any(), any(), any(), any());
        verify(shipmentService, never()).applyProviderUpdate(any());
    }

    @Test
    void invalidWebhookCausesNoDbMutation() {
        Carrier carrier = buildCarrier();
        CarrierConfig config = new CarrierConfig();
        when(carrierRepository.findFirstByProviderType(CarrierProviderType.AHAMOVE)).thenReturn(Optional.of(carrier));
        when(carrierConfigRepository.findByCarrierId(carrier.getId())).thenReturn(Optional.of(config));
        when(configResolver.resolve(config)).thenReturn(buildResolvedConfig());

        assertThatThrownBy(() -> webhookService.receiveWebhook("{invalid", Map.of("X-Webhook-Token", "token")))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(carrierWebhookLogPersister, never()).createInitialLog(any(), any(), any(), any(), any(), any(), any());
        verify(shipmentService, never()).applyProviderUpdate(any());
    }

    @Test
    void duplicateWebhook_isIdempotent() {
        Carrier carrier = buildCarrier();
        CarrierConfig config = new CarrierConfig();
        when(carrierRepository.findFirstByProviderType(CarrierProviderType.AHAMOVE)).thenReturn(Optional.of(carrier));
        when(carrierConfigRepository.findByCarrierId(carrier.getId())).thenReturn(Optional.of(config));
        when(configResolver.resolve(config)).thenReturn(buildResolvedConfig());
        when(carrierWebhookLogRepository.existsByEventKey(any())).thenReturn(true);

        webhookService.receiveWebhook(validPayload("ASSIGNING"), Map.of("X-Webhook-Token", "token"));

        verify(carrierWebhookLogPersister, never()).createInitialLog(any(), any(), any(), any(), any(), any(), any());
        verify(shipmentService, never()).applyProviderUpdate(any());
    }

    @Test
    void unknownStatus_doesNotCrash() {
        Carrier carrier = buildCarrier();
        CarrierConfig config = new CarrierConfig();
        Shipment shipment = buildShipment();
        CarrierWebhookLog log = new CarrierWebhookLog();

        when(carrierRepository.findFirstByProviderType(CarrierProviderType.AHAMOVE)).thenReturn(Optional.of(carrier));
        when(carrierConfigRepository.findByCarrierId(carrier.getId())).thenReturn(Optional.of(config));
        when(configResolver.resolve(config)).thenReturn(buildResolvedConfig());
        when(carrierWebhookLogRepository.existsByEventKey(any())).thenReturn(false);
        when(carrierWebhookLogPersister.createInitialLog(any(), any(), any(), any(), any(), any(), any())).thenReturn(log);
        when(shipmentRepository.findByCarrierShipmentId("AHA-1")).thenReturn(Optional.of(shipment));
        when(shipmentService.applyProviderUpdate(any())).thenReturn(null);

        webhookService.receiveWebhook(validPayload("MYSTERY"), Map.of("X-Webhook-Token", "token"));

        ArgumentCaptor<com.locnguyen.ecommerce.domains.shipment.service.ShipmentProviderUpdate> captor =
                ArgumentCaptor.forClass(com.locnguyen.ecommerce.domains.shipment.service.ShipmentProviderUpdate.class);
        verify(shipmentService).applyProviderUpdate(captor.capture());
        assertThat(captor.getValue().status()).isNull();
        assertThat(log.getErrorMessage()).contains("Unknown provider status");
    }

    private Carrier buildCarrier() {
        Carrier carrier = new Carrier();
        ReflectionTestUtils.setField(carrier, "id", UUID.fromString("00000000-0000-0000-0000-000000000111"));
        carrier.setCode("AHAMOVE");
        carrier.setProviderType(CarrierProviderType.AHAMOVE);
        return carrier;
    }

    private Shipment buildShipment() {
        Shipment shipment = new Shipment();
        ReflectionTestUtils.setField(shipment, "id", UUID.fromString("00000000-0000-0000-0000-000000000222"));
        shipment.setCarrierShipmentId("AHA-1");
        shipment.setTrackingNumber("TRK-1");
        return shipment;
    }

    private AhamoveResolvedConfig buildResolvedConfig() {
        return new AhamoveResolvedConfig(
                "https://partner-apistg.ahamove.com",
                "api-key",
                "84338710667",
                "Locen Studio",
                "token",
                "pickup",
                "pickup",
                "Locen Studio",
                "84338710667",
                null,
                null,
                "BIKE",
                "CASH",
                java.util.List.of()
        );
    }

    private String validPayload(String status) {
        return """
                {
                  "_id": "AHA-1",
                  "status": "%s",
                  "shared_link": "https://expressstg.ahamove.com/s/abc",
                  "path": [
                    {
                      "address": "Pickup",
                      "short_address": "Pickup",
                      "mobile": "84338710667"
                    },
                    {
                      "address": "Dropoff",
                      "short_address": "District 1",
                      "mobile": "0909000001",
                      "tracking_number": "TRK-1",
                      "status": "COMPLETED",
                      "complete_time": 1715742305
                    }
                  ]
                }
                """.formatted(status);
    }
}
