package com.locnguyen.ecommerce.domains.shipment.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProvider;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderCapabilities;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderRegistry;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.order.dto.CancellationOrderContext;
import com.locnguyen.ecommerce.domains.order.service.OrderService;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import com.locnguyen.ecommerce.domains.order.enums.PaymentStatus;
import com.locnguyen.ecommerce.domains.shipment.dto.CancelOrderRequest;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentCancellationOperation;
import com.locnguyen.ecommerce.domains.shipment.enums.BookingAttemptStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.CancellationOperationStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentCancellationOperationRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentEventRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderCancellationServiceTest {

    @Mock private OrderService orderService;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ShipmentCancellationOperationRepository operationRepository;
    @Mock private ShipmentEventRepository eventRepository;
    @Mock private CarrierProviderRegistry providerRegistry;
    @Mock private CarrierConfigRepository carrierConfigRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ObjectProvider<OrderCancellationService> self;
    @Mock private CarrierProvider provider;

    private OrderCancellationService service;
    private final UUID orderId = UUID.randomUUID();
    private final UUID shipmentId = UUID.randomUUID();
    private final UUID operationId = UUID.randomUUID();
    private final Order order = new Order();
    private final Shipment shipment = new Shipment();
    private final ShipmentCancellationOperation operation = new ShipmentCancellationOperation();

    @BeforeEach
    void setUp() {
        service = new OrderCancellationService(orderService, shipmentRepository, operationRepository,
                eventRepository, providerRegistry, carrierConfigRepository, auditLogService, self);
        ReflectionTestUtils.setField(order, "id", orderId);
        ReflectionTestUtils.setField(shipment, "id", shipmentId);
        ReflectionTestUtils.setField(operation, "id", operationId);
        order.setOrderCode("ORD-CANCEL-1");
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.PENDING);
        shipment.setOrder(order);
        operation.setOrderId(orderId);
        operation.setIdempotencyKey(UUID.randomUUID().toString());
        operation.setRequestedBy("admin");
        operation.setReason("Customer requested cancellation");
        operation.setStatus(CancellationOperationStatus.PENDING);
        when(self.getObject()).thenReturn(service);
        when(orderService.lockCancellationOrder(orderId)).thenAnswer(call -> context());
        when(orderService.getCancellationContext(orderId)).thenAnswer(call -> context());
        when(orderService.finalizeCancellation(any(), any(), any())).thenAnswer(call -> {
            order.setStatus(OrderStatus.CANCELLED);
            return context();
        });
        when(operationRepository.saveAndFlush(any(ShipmentCancellationOperation.class)))
                .thenAnswer(call -> {
                    ShipmentCancellationOperation saved = call.getArgument(0);
                    if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", operationId);
                    return saved;
                });
        when(operationRepository.save(any(ShipmentCancellationOperation.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(operationRepository.findById(operationId)).thenReturn(Optional.of(operation));
    }

    @Test
    void cancelOrder_withoutShipment_confirmsAndReleasesInventoryOnceAcrossRetries() {
        operation.setShipment(null);
        when(operationRepository.findByOrderId(orderId)).thenReturn(Optional.empty(), Optional.of(operation));
        when(shipmentRepository.findByOrderIdWithLock(orderId)).thenReturn(Optional.empty());
        CancelOrderRequest request = request();

        var first = service.cancelOrder(orderId, request);
        var retry = service.cancelOrder(orderId, request);

        assertThat(first.getStatus()).isEqualTo(CancellationOperationStatus.CONFIRMED);
        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderService).finalizeCancellation(orderId, "admin", "Customer requested cancellation");
    }

    @Test
    void cancelOrder_whenProviderTimesOut_keepsOrderActiveAndRetryDoesNotCallProviderAgain() {
        Carrier carrier = carrier();
        shipment.setCarrierEntity(carrier);
        shipment.setBookingStatus(BookingAttemptStatus.SUCCEEDED);
        shipment.setStatus(ShipmentStatus.PENDING);
        when(operationRepository.findByOrderId(orderId)).thenReturn(Optional.empty(), Optional.of(operation));
        when(shipmentRepository.findByOrderIdWithLock(orderId)).thenReturn(Optional.of(shipment));
        when(providerRegistry.find(CarrierProviderType.MOCK.name())).thenReturn(Optional.of(provider));
        when(provider.capabilities()).thenReturn(new CarrierProviderCapabilities(true, false, true, false, true));
        when(provider.supportsCancellation()).thenReturn(true);
        when(carrierConfigRepository.findByCarrierId(carrier.getId())).thenReturn(Optional.of(new CarrierConfig()));
        when(provider.cancelShipment(any(), any(), any())).thenThrow(new IllegalStateException("timeout"));
        when(operationRepository.saveAndFlush(any(ShipmentCancellationOperation.class)))
                .thenAnswer(call -> {
                    ShipmentCancellationOperation saved = call.getArgument(0);
                    if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", operationId);
                    saved.setShipment(shipment);
                    return saved;
                });

        var first = service.cancelOrder(orderId, request());
        var retry = service.cancelOrder(orderId, request());

        assertThat(first.getStatus()).isEqualTo(CancellationOperationStatus.UNKNOWN);
        assertThat(retry.getStatus()).isEqualTo(CancellationOperationStatus.UNKNOWN);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(provider).cancelShipment(any(), any(), any());
        verify(orderService, never()).finalizeCancellation(any(), any(), any());
    }

    @Test
    void cancelOrder_whenLegacyProviderBookingStateIsMissing_requiresReconciliation() {
        shipment.setCarrierEntity(carrier());
        shipment.setStatus(ShipmentStatus.PENDING);
        when(operationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(shipmentRepository.findByOrderIdWithLock(orderId)).thenReturn(Optional.of(shipment));

        var response = service.cancelOrder(orderId, request());

        assertThat(response.getStatus()).isEqualTo(CancellationOperationStatus.UNKNOWN);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(provider, never()).cancelShipment(any(), any(), any());
        verify(orderService, never()).finalizeCancellation(any(), any(), any());
    }

    @Test
    void cancelOrder_whenShipmentIsDelivered_isRejectedWithoutInventoryRelease() {
        shipment.setStatus(ShipmentStatus.DELIVERED);
        when(operationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(shipmentRepository.findByOrderIdWithLock(orderId)).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.cancelOrder(orderId, request()))
                .isInstanceOf(AppException.class);

        verify(operationRepository, never()).saveAndFlush(any());
        verify(orderService, never()).finalizeCancellation(any(), any(), any());
    }

    @Test
    void cancelOrder_whenPaidOnlineDoesNotChangePaymentStatusOrRefund() {
        operation.setShipment(null);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setPaymentStatus(PaymentStatus.PAID);
        when(operationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(shipmentRepository.findByOrderIdWithLock(orderId)).thenReturn(Optional.empty());

        var response = service.cancelOrder(orderId, request());

        assertThat(response.getStatus()).isEqualTo(CancellationOperationStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderService).finalizeCancellation(orderId, "admin", "Customer requested cancellation");
    }

    private CancelOrderRequest request() {
        CancelOrderRequest request = new CancelOrderRequest();
        request.setReason("Customer requested cancellation");
        return request;
    }

    private CancellationOrderContext context() {
        return new CancellationOrderContext(orderId, order.getOrderCode(), order.getStatus());
    }

    private Carrier carrier() {
        Carrier carrier = new Carrier();
        ReflectionTestUtils.setField(carrier, "id", UUID.randomUUID());
        carrier.setProviderType(CarrierProviderType.MOCK);
        return carrier;
    }
}
