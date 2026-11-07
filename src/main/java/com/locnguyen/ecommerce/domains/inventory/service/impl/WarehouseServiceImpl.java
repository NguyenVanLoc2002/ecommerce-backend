package com.locnguyen.ecommerce.domains.inventory.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.inventory.dto.CreateWarehouseRequest;
import com.locnguyen.ecommerce.domains.inventory.dto.UpdateWarehouseRequest;
import com.locnguyen.ecommerce.domains.inventory.dto.WarehouseFilter;
import com.locnguyen.ecommerce.domains.inventory.dto.WarehousePickupLocation;
import com.locnguyen.ecommerce.domains.inventory.dto.WarehouseResponse;
import com.locnguyen.ecommerce.domains.inventory.entity.Warehouse;
import com.locnguyen.ecommerce.domains.inventory.enums.WarehouseStatus;
import com.locnguyen.ecommerce.domains.inventory.mapper.WarehouseMapper;
import com.locnguyen.ecommerce.domains.inventory.repository.WarehouseRepository;
import com.locnguyen.ecommerce.domains.inventory.service.WarehouseService;
import com.locnguyen.ecommerce.domains.inventory.specification.WarehouseSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseResponse> getActiveWarehouses() {
        return warehouseRepository.findByStatusAndDeletedFalseOrderByCreatedAtAsc(WarehouseStatus.ACTIVE)
                .stream()
                .map(warehouseMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseResponse> getWarehouses(WarehouseFilter filter) {
        return warehouseRepository.findAll(WarehouseSpecification.withFilter(filter))
                .stream()
                .map(warehouseMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(UUID id) {
        return warehouseMapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.getCode().trim())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Warehouse code already exists");
        }

        Warehouse warehouse = new Warehouse();
        warehouse.setName(request.getName().trim());
        warehouse.setCode(request.getCode().trim());
        warehouse.setLocation(request.getLocation());
        applyPickupDetails(warehouse, request.getPickupAddress(), request.getPickupShortAddress(),
                request.getPickupContactName(), request.getPickupContactPhone(), request.getPickupLat(), request.getPickupLng());
        warehouse.setStatus(WarehouseStatus.ACTIVE);

        warehouse = warehouseRepository.save(warehouse);
        log.info("Warehouse created: id={} code={}", warehouse.getId(), warehouse.getCode());
        return warehouseMapper.toResponse(warehouse);
    }

    @Override
    @Transactional
    public WarehouseResponse updateWarehouse(UUID id, UpdateWarehouseRequest request) {
        Warehouse warehouse = findOrThrow(id);

        if (request.getName() != null) {
            warehouse.setName(request.getName().trim());
        }
        if (request.getLocation() != null) {
            warehouse.setLocation(request.getLocation());
        }
        if (request.getPickupAddress() != null) {
            warehouse.setPickupAddress(normalizeOptional(request.getPickupAddress()));
        }
        if (request.getPickupShortAddress() != null) {
            warehouse.setPickupShortAddress(normalizeOptional(request.getPickupShortAddress()));
        }
        if (request.getPickupContactName() != null) {
            warehouse.setPickupContactName(normalizeOptional(request.getPickupContactName()));
        }
        if (request.getPickupContactPhone() != null) {
            warehouse.setPickupContactPhone(normalizeOptional(request.getPickupContactPhone()));
        }
        if (request.getPickupLat() != null) {
            warehouse.setPickupLat(request.getPickupLat());
        }
        if (request.getPickupLng() != null) {
            warehouse.setPickupLng(request.getPickupLng());
        }
        if (request.getStatus() != null) {
            warehouse.setStatus(request.getStatus());
        }

        warehouse = warehouseRepository.save(warehouse);
        log.info("Warehouse updated: id={} by={}", id, SecurityUtils.getCurrentUsernameOrSystem());
        return warehouseMapper.toResponse(warehouse);
    }

    @Override
    @Transactional
    public void deleteWarehouse(UUID id) {
        Warehouse warehouse = findOrThrow(id);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        warehouse.softDelete(actor);
        warehouseRepository.save(warehouse);
        log.info("Warehouse deleted: id={} by={}", id, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public WarehousePickupLocation getPickupLocation(UUID id) {
        Warehouse warehouse = findOrThrow(id);
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Pickup warehouse must be active");
        }

        String address = normalizeOptional(warehouse.getPickupAddress());
        String contactName = normalizeOptional(warehouse.getPickupContactName());
        String contactPhone = normalizeOptional(warehouse.getPickupContactPhone());
        if (address == null || contactName == null || contactPhone == null
                || warehouse.getPickupLat() == null || warehouse.getPickupLng() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Pickup warehouse must include address, contact name, contact phone, latitude, and longitude");
        }

        return new WarehousePickupLocation(
                warehouse.getId(),
                address,
                normalizeOptional(warehouse.getPickupShortAddress()),
                contactName,
                contactPhone,
                warehouse.getPickupLat(),
                warehouse.getPickupLng());
    }

    @Override
    public Warehouse findOrThrow(UUID id) {
        return warehouseRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));
    }

    private void applyPickupDetails(
            Warehouse warehouse,
            String address,
            String shortAddress,
            String contactName,
            String contactPhone,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude) {
        warehouse.setPickupAddress(normalizeOptional(address));
        warehouse.setPickupShortAddress(normalizeOptional(shortAddress));
        warehouse.setPickupContactName(normalizeOptional(contactName));
        warehouse.setPickupContactPhone(normalizeOptional(contactPhone));
        warehouse.setPickupLat(latitude);
        warehouse.setPickupLng(longitude);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
