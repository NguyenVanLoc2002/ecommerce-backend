package com.locnguyen.ecommerce.domains.shipment.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProvider;
import com.locnguyen.ecommerce.domains.carrier.provider.BookingLookupResult;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingOrderResult;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderRegistry;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.shipment.dto.CreateShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.dto.ShipmentResponse;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.mapper.ShipmentMapper;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentEventRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import com.locnguyen.ecommerce.domains.shipment.service.impl.ShipmentBookingCoordinator;
import com.locnguyen.ecommerce.domains.shipment.service.impl.ShipmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentBookingFlowTest {

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ShipmentEventRepository eventRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ShipmentMapper shipmentMapper;
    @Mock private CarrierConfigRepository carrierConfigRepository;
    @Mock private CarrierProviderRegistry providerRegistry;
    @Mock private ShipmentBookingCoordinator bookingCoordinator;
    @Mock private CarrierProvider provider;
    @Mock private com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService auditLogService;

    private ShipmentServiceImpl service;
    private final UUID shipmentId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();
    private final Shipment shipment = new Shipment();
    private final Order order = new Order();
    private final CarrierConfig config = new CarrierConfig();
    private final CreateShipmentRequest request = new CreateShipmentRequest();
    private final ShipmentResponse response = ShipmentResponse.builder().id(shipmentId).build();

    @Test
    void providerMilestones_skipMissingScansWithoutRegressingOrCollectingCod() {
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        order.setStatus(com.locnguyen.ecommerce.domains.order.enums.OrderStatus.PROCESSING);
        order.setPaymentMethod(com.locnguyen.ecommerce.domains.order.enums.PaymentMethod.COD);
        order.setPaymentStatus(com.locnguyen.ecommerce.domains.order.enums.PaymentStatus.PENDING);
        shipment.setStatus(com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus.PENDING);
        when(shipmentRepository.findById(any())).thenReturn(java.util.Optional.of(shipment));
        when(orderRepository.findByIdWithLock(any())).thenReturn(java.util.Optional.of(order));
        service.applyProviderUpdate(ShipmentProviderUpdate.builder().shipmentId(shipmentId)
                .status(com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus.IN_TRANSIT).build());
        assertThat(order.getStatus()).isEqualTo(com.locnguyen.ecommerce.domains.order.enums.OrderStatus.SHIPPED);
        service.applyProviderUpdate(ShipmentProviderUpdate.builder().shipmentId(shipmentId)
                .status(com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus.PICKING).build());
        assertThat(shipment.getStatus()).isEqualTo(com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus.IN_TRANSIT);
        service.applyProviderUpdate(ShipmentProviderUpdate.builder().shipmentId(shipmentId)
                .status(com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus.DELIVERED).build());
        assertThat(order.getStatus()).isEqualTo(com.locnguyen.ecommerce.domains.order.enums.OrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(com.locnguyen.ecommerce.domains.order.enums.PaymentStatus.PENDING);
    }

    @Test
    void manualPickup_advancesOrderAndAuditsSource() {
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        order.setStatus(com.locnguyen.ecommerce.domains.order.enums.OrderStatus.PROCESSING);
        shipment.setStatus(com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus.PICKING);
        when(shipmentRepository.findById(any())).thenReturn(java.util.Optional.of(shipment));
        when(orderRepository.findByIdWithLock(any())).thenReturn(java.util.Optional.of(order));
        var update = new com.locnguyen.ecommerce.domains.shipment.dto.UpdateShipmentStatusRequest();
        update.setStatus(com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus.IN_TRANSIT);
        service.updateStatus(shipmentId, update);
        assertThat(order.getStatus()).isEqualTo(com.locnguyen.ecommerce.domains.order.enums.OrderStatus.SHIPPED);
        verify(auditLogService).logWithActor(
                org.mockito.ArgumentMatchers.eq(com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction.SHIPMENT_STATUS_UPDATED),
                org.mockito.ArgumentMatchers.eq("SHIPMENT"), org.mockito.ArgumentMatchers.eq(shipmentId.toString()),
                any(), org.mockito.ArgumentMatchers.contains("source=MANUAL"));
    }

    @Test
    void exceptionAttention_distinguishesCancellationAndPhysicalReturn() {
        assertThat(ShipmentResponse.builder()
                .status(com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus.FAILED)
                .providerStatus("CANCELLED").build().getAttention()).isEqualTo("CARRIER_CANCELLED");
        assertThat(ShipmentResponse.builder()
                .status(com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus.RETURNED)
                .build().getAttention()).isEqualTo("RETURNED_HANDLING");
    }

    @BeforeEach
    void setUp() {
        service = new ShipmentServiceImpl(shipmentRepository, eventRepository, orderRepository, shipmentMapper,
                carrierConfigRepository, providerRegistry, bookingCoordinator, auditLogService);
        shipment.setOrder(order);
    }

    @Test
    void createShipment_whenProviderConfirms_persistsSuccessfulAttempt() {
        var prepared = new ShipmentBookingCoordinator.PreparedBooking(
                shipmentId, attemptId, provider, config, idempotencyKey, shipment);
        var booking = ShippingOrderResult.builder().carrierShipmentId("provider-1").build();
        when(bookingCoordinator.prepare(request)).thenReturn(prepared);
        when(bookingCoordinator.getShipmentResponse(shipmentId)).thenReturn(response);
        when(provider.createShipment(shipment, order, config, idempotencyKey)).thenReturn(booking);

        ShipmentResponse result = service.createShipment(request);

        assertThat(result).isSameAs(response);
        verify(bookingCoordinator).complete(attemptId, booking);
        verify(bookingCoordinator, never()).markUnknown(any(), any());
    }

    @Test
    void createShipment_whenProviderTimesOut_keepsUnknownAttemptInsteadOfReportingFailure() {
        var prepared = new ShipmentBookingCoordinator.PreparedBooking(
                shipmentId, attemptId, provider, config, idempotencyKey, shipment);
        when(bookingCoordinator.prepare(request)).thenReturn(prepared);
        when(bookingCoordinator.getShipmentResponse(shipmentId)).thenReturn(response);
        when(provider.createShipment(shipment, order, config, idempotencyKey))
                .thenThrow(new IllegalStateException("connection timed out"));

        ShipmentResponse result = service.createShipment(request);

        assertThat(result).isSameAs(response);
        verify(bookingCoordinator).markUnknown(org.mockito.ArgumentMatchers.eq(attemptId), any());
        verify(bookingCoordinator, never()).complete(any(), any());
    }

    @Test
    void createShipment_whenLocalResultPersistenceFails_marksAttemptUnknown() {
        var prepared = new ShipmentBookingCoordinator.PreparedBooking(
                shipmentId, attemptId, provider, config, idempotencyKey, shipment);
        var booking = ShippingOrderResult.builder().carrierShipmentId("provider-2").build();
        when(bookingCoordinator.prepare(request)).thenReturn(prepared);
        when(bookingCoordinator.getShipmentResponse(shipmentId)).thenReturn(response);
        when(provider.createShipment(shipment, order, config, idempotencyKey)).thenReturn(booking);
        doThrow(new IllegalStateException("database unavailable"))
                .when(bookingCoordinator).complete(attemptId, booking);

        service.createShipment(request);

        verify(bookingCoordinator).markUnknown(org.mockito.ArgumentMatchers.eq(attemptId), any());
    }

    @Test
    void createShipment_forInternalManualCarrier_doesNotCallProvider() {
        when(bookingCoordinator.prepare(request)).thenReturn(
                new ShipmentBookingCoordinator.PreparedBooking(shipmentId, null, null, null, null, shipment));
        when(bookingCoordinator.getShipmentResponse(shipmentId)).thenReturn(response);

        service.createShipment(request);

        verify(provider, never()).createShipment(any(), any(), any(), any());
        verify(bookingCoordinator, never()).complete(any(), any());
    }

    @Test
    void createShipment_whenPreparationRejectsInactiveOrDuplicate_doesNotCallProvider() {
        when(bookingCoordinator.prepare(request)).thenThrow(new AppException(ErrorCode.SHIPMENT_ALREADY_EXISTS));

        assertThatThrownBy(() -> service.createShipment(request))
                .isInstanceOf(AppException.class);

        verify(provider, never()).createShipment(any(), any(), any(), any());
    }

    @Test
    void providersDefaultToNoAutomaticRetryAndUnknownLookup() {
        assertThat(com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderCapabilities.unsupported()
                .idempotentBooking()).isFalse();
        assertThat(com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderCapabilities.unsupported()
                .bookingLookup()).isFalse();
        assertThat(com.locnguyen.ecommerce.domains.carrier.provider.BookingLookupResult.unknown().status())
                .isEqualTo(BookingLookupResult.Status.UNKNOWN);
    }
}
