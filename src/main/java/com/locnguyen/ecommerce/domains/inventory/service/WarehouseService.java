package com.locnguyen.ecommerce.domains.inventory.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.inventory.dto.WarehouseResponse;
import com.locnguyen.ecommerce.domains.inventory.dto.CreateWarehouseRequest;
import com.locnguyen.ecommerce.domains.inventory.dto.UpdateWarehouseRequest;
import com.locnguyen.ecommerce.domains.inventory.entity.Warehouse;
import com.locnguyen.ecommerce.domains.inventory.enums.WarehouseStatus;
import com.locnguyen.ecommerce.domains.inventory.mapper.WarehouseMapper;
import com.locnguyen.ecommerce.domains.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;

    // ─── Public ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WarehouseResponse> getActiveWarehouses() {
        return warehouseRepository.findByStatusOrderByCreatedAtAsc(WarehouseStatus.ACTIVE)
                .stream().map(warehouseMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(Long id) {
        return warehouseMapper.toResponse(findOrThrow(id));
    }

    // ─── Admin CRUD ──────────────────────────────────────────────────────────

    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.getCode().trim())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Warehouse code already exists");
        }

        Warehouse warehouse = new Warehouse();
        warehouse.setName(request.getName().trim());
        warehouse.setCode(request.getCode().trim());
        warehouse.setLocation(request.getLocation());
        warehouse.setStatus(WarehouseStatus.ACTIVE);

        warehouse = warehouseRepository.save(warehouse);
        log.info("Warehouse created: id={} code={}", warehouse.getId(), warehouse.getCode());
        return warehouseMapper.toResponse(warehouse);
    }

    @Transactional
    public WarehouseResponse updateWarehouse(Long id, UpdateWarehouseRequest request) {
        Warehouse warehouse = findOrThrow(id);

        if (request.getName() != null) {
            warehouse.setName(request.getName().trim());
        }
        if (request.getLocation() != null) {
            warehouse.setLocation(request.getLocation());
        }
        if (request.getStatus() != null) {
            warehouse.setStatus(request.getStatus());
        }

        warehouse = warehouseRepository.save(warehouse);
        log.info("Warehouse updated: id={} by={}", id, SecurityUtils.getCurrentUsernameOrSystem());
        return warehouseMapper.toResponse(warehouse);
    }

    @Transactional
    public void deleteWarehouse(Long id) {
        Warehouse warehouse = findOrThrow(id);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        warehouse.softDelete(actor);
        warehouseRepository.save(warehouse);
        log.info("Warehouse deleted: id={} by={}", id, actor);
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    Warehouse findOrThrow(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));
    }
}
