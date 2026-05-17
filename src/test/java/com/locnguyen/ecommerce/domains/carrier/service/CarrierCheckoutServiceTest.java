package com.locnguyen.ecommerce.domains.carrier.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.dto.CheckoutCarrierOptionResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.CheckoutCarrierQuoteResponse;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProvider;
import com.locnguyen.ecommerce.domains.carrier.provider.CarrierProviderRegistry;
import com.locnguyen.ecommerce.domains.carrier.provider.ShippingRateResult;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierRepository;
import com.locnguyen.ecommerce.domains.carrier.service.impl.CarrierCheckoutServiceImpl;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarrierCheckoutServiceTest {

    @Mock CarrierRepository carrierRepository;
    @Mock CarrierConfigRepository carrierConfigRepository;
    @Mock CarrierProviderRegistry carrierProviderRegistry;
    @Mock CarrierProvider carrierProvider;

    @InjectMocks CarrierCheckoutServiceImpl carrierCheckoutService;

    @Test
    void getCheckoutOptions_returnsOnlyActiveSupportedConfiguredCarriers() {
        Carrier activeConfigured = carrier(uuid(1), "AHAMOVE", CarrierProviderType.AHAMOVE, CarrierStatus.ACTIVE);
        Carrier missingProvider = carrier(uuid(3), "GHN", CarrierProviderType.GHN, CarrierStatus.ACTIVE);

        when(carrierRepository.findAllByStatus(CarrierStatus.ACTIVE)).thenReturn(List.of(activeConfigured, missingProvider));
        when(carrierProviderRegistry.find(CarrierProviderType.AHAMOVE.name())).thenReturn(Optional.of(carrierProvider));
        when(carrierProviderRegistry.find(CarrierProviderType.GHN.name())).thenReturn(Optional.empty());

        CarrierConfig config = new CarrierConfig();
        config.setEnabled(true);
        when(carrierConfigRepository.findByCarrierId(uuid(1))).thenReturn(Optional.of(config));

        List<CheckoutCarrierOptionResponse> options = carrierCheckoutService.getCheckoutOptions();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getId()).isEqualTo(uuid(1));
        assertThat(options.get(0).getCode()).isEqualTo("AHAMOVE");
    }

    @Test
    void quote_returnsPricedCarrierSnapshot() {
        Carrier carrier = carrier(uuid(9), "MOCK", CarrierProviderType.MOCK, CarrierStatus.ACTIVE);
        when(carrierRepository.findById(uuid(9))).thenReturn(Optional.of(carrier));
        when(carrierProviderRegistry.find(CarrierProviderType.MOCK.name())).thenReturn(Optional.of(carrierProvider));
        when(carrierProvider.calculateRate(any(), any())).thenReturn(
                ShippingRateResult.builder()
                        .fee(new BigDecimal("40000"))
                        .currency("VND")
                        .serviceName("Mock same-day")
                        .build());

        CheckoutCarrierQuoteResponse response = carrierCheckoutService.quote(uuid(9), new Order());

        assertThat(response.getCarrierId()).isEqualTo(uuid(9));
        assertThat(response.getCarrierCode()).isEqualTo("MOCK");
        assertThat(response.getShippingFee()).isEqualByComparingTo("40000");
    }

    @Test
    void quote_inactiveCarrier_throwsCarrierRequestFailed() {
        Carrier carrier = carrier(uuid(10), "MOCK", CarrierProviderType.MOCK, CarrierStatus.INACTIVE);
        when(carrierRepository.findById(uuid(10))).thenReturn(Optional.of(carrier));

        assertThatThrownBy(() -> carrierCheckoutService.quote(uuid(10), new Order()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CARRIER_REQUEST_FAILED);
    }

    private Carrier carrier(UUID id, String code, CarrierProviderType providerType, CarrierStatus status) {
        Carrier carrier = new Carrier();
        ReflectionTestUtils.setField(carrier, "id", id);
        ReflectionTestUtils.setField(carrier, "createdAt", LocalDateTime.now());
        carrier.setCode(code);
        carrier.setName(code);
        carrier.setProviderType(providerType);
        carrier.setStatus(status);
        return carrier;
    }

    private static UUID uuid(long n) {
        return new UUID(0L, n);
    }
}
