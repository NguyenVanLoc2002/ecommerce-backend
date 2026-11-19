package com.locnguyen.ecommerce.domains.shipment.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProvider;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderRegistry;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierRepository;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.OrderStatus;
import com.locnguyen.ecommerce.domains.order.repository.OrderRepository;
import com.locnguyen.ecommerce.domains.shipment.dto.CreateShipmentRequest;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.entity.ShipmentBookingAttempt;
import com.locnguyen.ecommerce.domains.shipment.enums.BookingAttemptStatus;
import com.locnguyen.ecommerce.domains.shipment.enums.CarrierOverrideReason;
import com.locnguyen.ecommerce.domains.shipment.mapper.ShipmentMapper;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentBookingAttemptRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentEventRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentBookingCoordinatorTest {

    @Mock private ShipmentRepository shipmentRepository;
    @Mock private ShipmentEventRepository shipmentEventRepository;
    @Mock private ShipmentBookingAttemptRepository attemptRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CarrierRepository carrierRepository;
    @Mock private CarrierConfigRepository carrierConfigRepository;
    @Mock private CarrierProviderRegistry providerRegistry;
    @Mock private AuditLogService auditLogService;
    @Mock private ShipmentMapper shipmentMapper;
    @Mock private CarrierProvider provider;

    @InjectMocks private ShipmentBookingCoordinator coordinator;

    private final UUID orderId = UUID.randomUUID();
    private final UUID carrierId = UUID.randomUUID();
    private final Order order = new Order();
    private final Carrier carrier = new Carrier();
    private final CreateShipmentRequest request = new CreateShipmentRequest();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(order, "id", orderId);
        ReflectionTestUtils.setField(carrier, "id", carrierId);
        order.setStatus(OrderStatus.PROCESSING);
        request.setOrderId(orderId);
        request.setCarrierId(carrierId);
        carrier.setName("AhaMove");
        carrier.setCode("AHAMOVE");
        carrier.setProviderType(CarrierProviderType.AHAMOVE);
        when(orderRepository.findByIdWithItemsAndLock(orderId)).thenReturn(Optional.of(order));
        when(shipmentRepository.existsByOrderId(orderId)).thenReturn(false);
        lenient().when(shipmentRepository.saveAndFlush(any(Shipment.class))).thenAnswer(invocation -> {
            Shipment shipment = invocation.getArgument(0);
            if (shipment.getId() == null) ReflectionTestUtils.setField(shipment, "id", UUID.randomUUID());
            return shipment;
        });
        lenient().when(attemptRepository.saveAndFlush(any(ShipmentBookingAttempt.class))).thenAnswer(invocation -> {
            ShipmentBookingAttempt attempt = invocation.getArgument(0);
            ReflectionTestUtils.setField(attempt, "id", UUID.randomUUID());
            return attempt;
        });
        lenient().when(carrierRepository.findById(carrierId)).thenReturn(Optional.of(carrier));
        lenient().when(providerRegistry.find(CarrierProviderType.AHAMOVE.name())).thenReturn(Optional.of(provider));
        CarrierConfig config = new CarrierConfig();
        config.setEnabled(true);
        lenient().when(carrierConfigRepository.findByCarrierId(carrierId)).thenReturn(Optional.of(config));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void prepare_persistsShipmentAndAttemptBeforeReturningProviderCallContext() {
        var prepared = coordinator.prepare(request);

        ArgumentCaptor<ShipmentBookingAttempt> attemptCaptor = ArgumentCaptor.forClass(ShipmentBookingAttempt.class);
        verify(attemptRepository).saveAndFlush(attemptCaptor.capture());
        assertThat(prepared.attemptId()).isNotNull();
        assertThat(prepared.idempotencyKey()).isNotBlank();
        assertThat(prepared.shipment().getBookingStatus()).isEqualTo(BookingAttemptStatus.PENDING);
        assertThat(attemptCaptor.getValue().getStatus()).isEqualTo(BookingAttemptStatus.PENDING);
        assertThat(attemptCaptor.getValue().getRequestFingerprint()).hasSize(64);
    }

    @Test
    void prepare_whenCheckoutCarrierIsInactive_doesNotPersistOrCallProvider() {
        carrier.setStatus(CarrierStatus.INACTIVE);

        assertThatThrownBy(() -> coordinator.prepare(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CARRIER_REQUEST_FAILED);

        verify(shipmentRepository, never()).saveAndFlush(any(Shipment.class));
        verify(attemptRepository, never()).saveAndFlush(any(ShipmentBookingAttempt.class));
        verify(providerRegistry, never()).find(any());
    }

    @Test
    void prepare_whenDuplicateShipmentExists_doesNotCreateAnotherAttempt() {
        when(shipmentRepository.existsByOrderId(orderId)).thenReturn(true);

        assertThatThrownBy(() -> coordinator.prepare(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SHIPMENT_ALREADY_EXISTS);

        verify(attemptRepository, never()).saveAndFlush(any(ShipmentBookingAttempt.class));
    }

    @Test
    void prepare_whenStaffAttemptsCarrierOverride_isForbidden() {
        order.setCarrierId(carrierId);
        request.setUseManualCarrier(true);
        request.setCarrier("Local carrier");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff", "", java.util.List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));

        assertThatThrownBy(() -> coordinator.prepare(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(shipmentRepository, never()).saveAndFlush(any(Shipment.class));
    }

    @Test
    void prepare_whenOtherOverrideReasonHasNoNote_isRejected() {
        order.setCarrierId(carrierId);
        request.setUseManualCarrier(true);
        request.setCarrier("Local carrier");
        request.setOverrideReasonCode(CarrierOverrideReason.OTHER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "", java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThatThrownBy(() -> coordinator.prepare(request))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(shipmentRepository, never()).saveAndFlush(any(Shipment.class));
    }
}
