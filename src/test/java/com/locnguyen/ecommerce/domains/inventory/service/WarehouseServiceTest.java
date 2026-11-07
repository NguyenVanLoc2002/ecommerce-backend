package com.locnguyen.ecommerce.domains.inventory.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.inventory.entity.Warehouse;
import com.locnguyen.ecommerce.domains.inventory.enums.WarehouseStatus;
import com.locnguyen.ecommerce.domains.inventory.mapper.WarehouseMapper;
import com.locnguyen.ecommerce.domains.inventory.repository.WarehouseRepository;
import com.locnguyen.ecommerce.domains.inventory.service.impl.WarehouseServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock WarehouseRepository warehouseRepository;
    @Mock WarehouseMapper warehouseMapper;
    @InjectMocks WarehouseServiceImpl warehouseService;

    @Test
    void getPickupLocation_whenWarehouseIsCompleteAndActive_returnsManagedPickupDetails() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = completeWarehouse(warehouseId);
        when(warehouseRepository.findByIdAndDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));

        var pickup = warehouseService.getPickupLocation(warehouseId);

        assertThat(pickup.warehouseId()).isEqualTo(warehouseId);
        assertThat(pickup.address()).isEqualTo("12 Nguyen Hue, District 1");
        assertThat(pickup.contactPhone()).isEqualTo("84338710667");
        assertThat(pickup.latitude()).isEqualByComparingTo("10.7765000");
    }

    @Test
    void getPickupLocation_whenWarehouseIsInactive_throwsValidationError() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = completeWarehouse(warehouseId);
        warehouse.setStatus(WarehouseStatus.INACTIVE);
        when(warehouseRepository.findByIdAndDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> warehouseService.getPickupLocation(warehouseId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void getPickupLocation_whenPickupProfileIsIncomplete_throwsValidationError() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = completeWarehouse(warehouseId);
        warehouse.setPickupLat(null);
        when(warehouseRepository.findByIdAndDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> warehouseService.getPickupLocation(warehouseId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    private Warehouse completeWarehouse(UUID id) {
        Warehouse warehouse = new Warehouse();
        ReflectionTestUtils.setField(warehouse, "id", id);
        warehouse.setStatus(WarehouseStatus.ACTIVE);
        warehouse.setPickupAddress("12 Nguyen Hue, District 1");
        warehouse.setPickupShortAddress("District 1");
        warehouse.setPickupContactName("Locen Studio");
        warehouse.setPickupContactPhone("84338710667");
        warehouse.setPickupLat(new BigDecimal("10.7765000"));
        warehouse.setPickupLng(new BigDecimal("106.7009000"));
        return warehouse;
    }
}
