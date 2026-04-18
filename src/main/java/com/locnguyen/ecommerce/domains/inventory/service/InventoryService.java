package com.locnguyen.ecommerce.domains.inventory.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.inventory.dto.*;
import com.locnguyen.ecommerce.domains.inventory.entity.Inventory;
import com.locnguyen.ecommerce.domains.inventory.entity.InventoryReservation;
import com.locnguyen.ecommerce.domains.inventory.entity.StockMovement;
import com.locnguyen.ecommerce.domains.inventory.entity.Warehouse;
import com.locnguyen.ecommerce.domains.inventory.enums.ReservationStatus;
import com.locnguyen.ecommerce.domains.inventory.enums.StockMovementType;
import com.locnguyen.ecommerce.domains.inventory.mapper.InventoryMapper;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryRepository;
import com.locnguyen.ecommerce.domains.inventory.repository.InventoryReservationRepository;
import com.locnguyen.ecommerce.domains.inventory.repository.StockMovementRepository;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseService warehouseService;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryMapper inventoryMapper;

    // ─── Read operations ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventoryByVariant(Long variantId) {
        List<Inventory> inventories = inventoryRepository.findByVariantId(variantId);
        return inventories.stream().map(inventoryMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventoryByWarehouse(Long warehouseId) {
        warehouseService.findOrThrow(warehouseId);
        List<Inventory> inventories = inventoryRepository.findByWarehouseId(warehouseId);
        return inventories.stream().map(inventoryMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryDetail(Long variantId, Long warehouseId) {
        Inventory inventory = inventoryRepository.findByVariantIdAndWarehouseId(variantId, warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));
        return inventoryMapper.toResponse(inventory);
    }

    @Transactional(readOnly = true)
    public PagedResponse<StockMovementResponse> getStockMovements(StockFilter filter, Pageable pageable) {
        StockMovementType movementType = filter.getMovementType() != null
                ? StockMovementType.valueOf(filter.getMovementType()) : null;
        Page<StockMovement> page = stockMovementRepository.filter(
                filter.getVariantId(), filter.getWarehouseId(), movementType, pageable);
        List<StockMovementResponse> items = page.getContent().stream()
                .map(inventoryMapper::toResponse).toList();
        return PagedResponse.of(items, page);
    }

    // ─── Stock adjustment operations ─────────────────────────────────────────

    /**
     * Import stock — increases on_hand. Creates inventory record if not exists.
     */
    @Transactional
    public StockMovementResponse importStock(Long variantId, Long warehouseId, int quantity, String note) {
        validateVariant(variantId);
        warehouseService.findOrThrow(warehouseId);

        Inventory inventory = getOrCreateInventory(variantId, warehouseId);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        int beforeOnHand = inventory.getOnHand();
        int beforeReserved = inventory.getReserved();
        int beforeAvailable = beforeOnHand - beforeReserved;

        inventoryRepository.increaseOnHand(inventory.getId(), quantity);
        inventoryRepository.flush();

        // Re-read after update for accurate after-values
        Inventory updated = inventoryRepository.findById(inventory.getId()).orElseThrow();
        StockMovement movement = recordMovement(
                inventory, StockMovementType.IMPORT, quantity,
                beforeOnHand, beforeReserved, beforeAvailable,
                updated.getOnHand(), updated.getReserved(), updated.getAvailable(),
                note, actor
        );

        log.info("Stock imported: variantId={} warehouseId={} qty={} by={}",
                variantId, warehouseId, quantity, actor);
        return inventoryMapper.toResponse(movement);
    }

    /**
     * Export stock — decreases on_hand. Cannot go below reserved count.
     */
    @Transactional
    public StockMovementResponse exportStock(Long variantId, Long warehouseId, int quantity, String note) {
        validateVariant(variantId);
        warehouseService.findOrThrow(warehouseId);

        Inventory inventory = inventoryRepository.findByVariantIdAndWarehouseId(variantId, warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        int beforeOnHand = inventory.getOnHand();
        int beforeReserved = inventory.getReserved();
        int beforeAvailable = beforeOnHand - beforeReserved;

        int updated = inventoryRepository.decreaseOnHand(inventory.getId(), quantity);
        if (updated == 0) {
            throw new AppException(ErrorCode.INVENTORY_NOT_ENOUGH,
                    "Cannot export: would leave on_hand below reserved count");
        }
        inventoryRepository.flush();

        Inventory refreshed = inventoryRepository.findById(inventory.getId()).orElseThrow();
        StockMovement movement = recordMovement(
                inventory, StockMovementType.EXPORT, quantity,
                beforeOnHand, beforeReserved, beforeAvailable,
                refreshed.getOnHand(), refreshed.getReserved(), refreshed.getAvailable(),
                note, actor
        );

        log.info("Stock exported: variantId={} warehouseId={} qty={} by={}",
                variantId, warehouseId, quantity, actor);
        return inventoryMapper.toResponse(movement);
    }

    /**
     * Adjust stock — manual inventory adjustment. Positive quantity increases, negative decreases.
     */
    @Transactional
    public StockMovementResponse adjustStock(AdjustStockRequest request) {
        validateVariant(request.getVariantId());
        warehouseService.findOrThrow(request.getWarehouseId());

        StockMovementType type = StockMovementType.valueOf(request.getMovementType());

        Inventory inventory = inventoryRepository
                .findByVariantIdAndWarehouseId(request.getVariantId(), request.getWarehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        int beforeOnHand = inventory.getOnHand();
        int beforeReserved = inventory.getReserved();
        int beforeAvailable = beforeOnHand - beforeReserved;

        applyAdjustment(inventory, type, request.getQuantity());
        inventoryRepository.flush();

        Inventory refreshed = inventoryRepository.findById(inventory.getId()).orElseThrow();
        StockMovement movement = recordMovement(
                inventory, type, request.getQuantity(),
                beforeOnHand, beforeReserved, beforeAvailable,
                refreshed.getOnHand(), refreshed.getReserved(), refreshed.getAvailable(),
                request.getNote(), actor
        );

        log.info("Stock adjusted: variantId={} warehouseId={} type={} qty={} by={}",
                request.getVariantId(), request.getWarehouseId(), type, request.getQuantity(), actor);
        return inventoryMapper.toResponse(movement);
    }

    /**
     * Return stock — increases on_hand (e.g., customer returns goods).
     */
    @Transactional
    public StockMovementResponse returnStock(Long variantId, Long warehouseId, int quantity,
                                              String referenceType, String referenceId) {
        validateVariant(variantId);
        warehouseService.findOrThrow(warehouseId);

        Inventory inventory = inventoryRepository.findByVariantIdAndWarehouseId(variantId, warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        int beforeOnHand = inventory.getOnHand();
        int beforeReserved = inventory.getReserved();
        int beforeAvailable = beforeOnHand - beforeReserved;

        inventoryRepository.increaseOnHand(inventory.getId(), quantity);
        inventoryRepository.flush();

        Inventory refreshed = inventoryRepository.findById(inventory.getId()).orElseThrow();
        StockMovement movement = recordMovement(
                inventory, StockMovementType.RETURN, quantity,
                beforeOnHand, beforeReserved, beforeAvailable,
                refreshed.getOnHand(), refreshed.getReserved(), refreshed.getAvailable(),
                "Returned stock", actor, referenceType, referenceId
        );

        log.info("Stock returned: variantId={} warehouseId={} qty={} ref={}:{} by={}",
                variantId, warehouseId, quantity, referenceType, referenceId, actor);
        return inventoryMapper.toResponse(movement);
    }

    // ─── Reservation operations ──────────────────────────────────────────────

    /**
     * Reserve stock for an order. Increases reserved. Atomic check prevents oversell.
     */
    @Transactional
    public InventoryReservation reserveStock(ReserveStockRequest request) {
        validateVariant(request.getVariantId());
        Warehouse warehouse = warehouseService.findOrThrow(request.getWarehouseId());

        Inventory inventory = inventoryRepository
                .findByVariantIdAndWarehouseIdWithLock(request.getVariantId(), request.getWarehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));

        int available = inventory.getOnHand() - inventory.getReserved();
        if (available < request.getQuantity()) {
            throw new AppException(ErrorCode.INVENTORY_NOT_ENOUGH,
                    "Available stock is " + available + ", requested " + request.getQuantity());
        }

        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        int beforeOnHand = inventory.getOnHand();
        int beforeReserved = inventory.getReserved();
        int beforeAvailable = beforeOnHand - beforeReserved;

        int updated = inventoryRepository.increaseReserved(inventory.getId(), request.getQuantity());
        if (updated == 0) {
            throw new AppException(ErrorCode.STOCK_RESERVATION_FAILED);
        }
        inventoryRepository.flush();

        Inventory refreshed = inventoryRepository.findById(inventory.getId()).orElseThrow();
        recordMovement(
                inventory, StockMovementType.RESERVE, request.getQuantity(),
                beforeOnHand, beforeReserved, beforeAvailable,
                refreshed.getOnHand(), refreshed.getReserved(), refreshed.getAvailable(),
                "Reserved for " + request.getReferenceType() + ":" + request.getReferenceId(),
                actor, request.getReferenceType(), request.getReferenceId()
        );

        InventoryReservation reservation = new InventoryReservation();
        reservation.setVariant(inventory.getVariant());
        reservation.setWarehouse(warehouse);
        reservation.setReferenceType(request.getReferenceType());
        reservation.setReferenceId(request.getReferenceId());
        reservation.setQuantity(request.getQuantity());
        reservation.setExpiresAt(request.getExpiresAt());
        reservation = reservationRepository.save(reservation);

        log.info("Stock reserved: variantId={} warehouseId={} qty={} ref={}:{} by={}",
                request.getVariantId(), request.getWarehouseId(), request.getQuantity(),
                request.getReferenceType(), request.getReferenceId(), actor);
        return reservation;
    }

    /**
     * Release reserved stock (on order cancel). Decreases reserved.
     */
    @Transactional
    public void releaseStock(String referenceType, String referenceId) {
        List<InventoryReservation> reservations = reservationRepository
                .findByReferenceTypeAndReferenceIdAndStatus(referenceType, referenceId, ReservationStatus.PENDING);

        if (reservations.isEmpty()) {
            log.warn("No pending reservations found for ref={}:{}", referenceType, referenceId);
            return;
        }

        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        for (InventoryReservation reservation : reservations) {
            Inventory inventory = inventoryRepository
                    .findByVariantIdAndWarehouseIdWithLock(
                            reservation.getVariant().getId(),
                            reservation.getWarehouse().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));

            int beforeOnHand = inventory.getOnHand();
            int beforeReserved = inventory.getReserved();
            int beforeAvailable = beforeOnHand - beforeReserved;

            int updated = inventoryRepository.decreaseReserved(inventory.getId(), reservation.getQuantity());
            if (updated == 0) {
                log.error("Failed to release reservation: id={} qty={}", reservation.getId(), reservation.getQuantity());
                continue;
            }
            inventoryRepository.flush();

            Inventory refreshed = inventoryRepository.findById(inventory.getId()).orElseThrow();
            recordMovement(
                    inventory, StockMovementType.RELEASE, reservation.getQuantity(),
                    beforeOnHand, beforeReserved, beforeAvailable,
                    refreshed.getOnHand(), refreshed.getReserved(), refreshed.getAvailable(),
                    "Released from " + referenceType + ":" + referenceId,
                    actor, referenceType, referenceId
            );

            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);

            log.info("Stock released: variantId={} qty={} ref={}:{} by={}",
                    reservation.getVariant().getId(), reservation.getQuantity(),
                    referenceType, referenceId, actor);
        }
    }

    /**
     * Release reserved stock for a specific variant (on order cancel per line item).
     */
    @Transactional
    public void releaseStockForVariant(String referenceType, String referenceId, Long variantId) {
        InventoryReservation reservation = reservationRepository
                .findByReferenceTypeAndReferenceIdAndVariantIdAndStatus(
                        referenceType, referenceId, variantId, ReservationStatus.PENDING)
                .orElse(null);

        if (reservation == null) {
            log.warn("No pending reservation found for ref={}:{} variantId={}",
                    referenceType, referenceId, variantId);
            return;
        }

        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        Inventory inventory = inventoryRepository
                .findByVariantIdAndWarehouseIdWithLock(
                        variantId, reservation.getWarehouse().getId())
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));

        int beforeOnHand = inventory.getOnHand();
        int beforeReserved = inventory.getReserved();
        int beforeAvailable = beforeOnHand - beforeReserved;

        int updated = inventoryRepository.decreaseReserved(inventory.getId(), reservation.getQuantity());
        if (updated == 0) {
            throw new AppException(ErrorCode.STOCK_RESERVATION_FAILED,
                    "Failed to release reserved stock for variant " + variantId);
        }
        inventoryRepository.flush();

        Inventory refreshed = inventoryRepository.findById(inventory.getId()).orElseThrow();
        recordMovement(
                inventory, StockMovementType.RELEASE, reservation.getQuantity(),
                beforeOnHand, beforeReserved, beforeAvailable,
                refreshed.getOnHand(), refreshed.getReserved(), refreshed.getAvailable(),
                "Released from " + referenceType + ":" + referenceId,
                actor, referenceType, referenceId
        );

        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.save(reservation);

        log.info("Stock released: variantId={} qty={} ref={}:{} by={}",
                variantId, reservation.getQuantity(), referenceType, referenceId, actor);
    }

    /**
     * Complete order — decrease both on_hand and reserved (physical shipment).
     */
    @Transactional
    public void completeOrder(String referenceType, String referenceId) {
        List<InventoryReservation> reservations = reservationRepository
                .findByReferenceTypeAndReferenceIdAndStatus(referenceType, referenceId, ReservationStatus.PENDING);

        if (reservations.isEmpty()) {
            log.warn("No pending reservations found for ref={}:{}", referenceType, referenceId);
            return;
        }

        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        for (InventoryReservation reservation : reservations) {
            Inventory inventory = inventoryRepository
                    .findByVariantIdAndWarehouseIdWithLock(
                            reservation.getVariant().getId(),
                            reservation.getWarehouse().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));

            int beforeOnHand = inventory.getOnHand();
            int beforeReserved = inventory.getReserved();
            int beforeAvailable = beforeOnHand - beforeReserved;

            int updated = inventoryRepository.decreaseOnHandAndReserved(
                    inventory.getId(), reservation.getQuantity());
            if (updated == 0) {
                log.error("Failed to complete order stock: reservationId={} qty={}",
                        reservation.getId(), reservation.getQuantity());
                continue;
            }
            inventoryRepository.flush();

            Inventory refreshed = inventoryRepository.findById(inventory.getId()).orElseThrow();
            recordMovement(
                    inventory, StockMovementType.EXPORT, reservation.getQuantity(),
                    beforeOnHand, beforeReserved, beforeAvailable,
                    refreshed.getOnHand(), refreshed.getReserved(), refreshed.getAvailable(),
                    "Order completed " + referenceType + ":" + referenceId,
                    actor, referenceType, referenceId
            );

            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);
        }

        log.info("Order stock completed: ref={}:{} reservations={} by={}",
                referenceType, referenceId, reservations.size(), actor);
    }

    // ─── Auto-release expired reservations ───────────────────────────────────

    /**
     * Scheduled task to auto-release expired reservations.
     * Called by a @Scheduled method or on application startup.
     */
    @Transactional
    public int releaseExpiredReservations() {
        List<InventoryReservation> expired = reservationRepository
                .findExpiredReservations(LocalDateTime.now());

        if (expired.isEmpty()) {
            return 0;
        }

        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        int released = 0;

        for (InventoryReservation reservation : expired) {
            try {
                releaseStockForVariant(
                        reservation.getReferenceType(),
                        reservation.getReferenceId(),
                        reservation.getVariant().getId()
                );
                released++;
            } catch (Exception e) {
                log.error("Failed to auto-release expired reservation: id={}", reservation.getId(), e);
            }
        }

        if (released > 0) {
            log.info("Auto-released {} expired reservations by={}", released, actor);
        }
        return released;
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    private void validateVariant(Long variantId) {
        if (!productVariantRepository.existsById(variantId)) {
            throw new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND);
        }
    }

    private Inventory getOrCreateInventory(Long variantId, Long warehouseId) {
        return inventoryRepository.findByVariantIdAndWarehouseId(variantId, warehouseId)
                .orElseGet(() -> {
                    Warehouse warehouse = warehouseService.findOrThrow(warehouseId);
                    ProductVariant variant = productVariantRepository.findById(variantId)
                            .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));

                    Inventory newInv = new Inventory();
                    newInv.setVariant(variant);
                    newInv.setWarehouse(warehouse);
                    return inventoryRepository.save(newInv);
                });
    }

    private void applyAdjustment(Inventory inventory, StockMovementType type, int quantity) {
        switch (type) {
            case IMPORT, RETURN -> inventoryRepository.increaseOnHand(inventory.getId(), quantity);
            case EXPORT -> {
                int updated = inventoryRepository.decreaseOnHand(inventory.getId(), quantity);
                if (updated == 0) {
                    throw new AppException(ErrorCode.INVENTORY_NOT_ENOUGH,
                            "Cannot adjust: would leave on_hand below reserved count");
                }
            }
            case ADJUST -> {
                // Positive quantity: increase on_hand. Negative: decrease on_hand (admin correction).
                if (quantity >= 0) {
                    inventoryRepository.increaseOnHand(inventory.getId(), quantity);
                } else {
                    int updated = inventoryRepository.decreaseOnHand(inventory.getId(), -quantity);
                    if (updated == 0) {
                        throw new AppException(ErrorCode.INVENTORY_NOT_ENOUGH,
                                "Cannot adjust: would leave on_hand below reserved count");
                    }
                }
            }
            case RESERVE -> {
                int updated = inventoryRepository.increaseReserved(inventory.getId(), quantity);
                if (updated == 0) {
                    throw new AppException(ErrorCode.STOCK_RESERVATION_FAILED);
                }
            }
            case RELEASE -> {
                int updated = inventoryRepository.decreaseReserved(inventory.getId(), quantity);
                if (updated == 0) {
                    throw new AppException(ErrorCode.INVENTORY_NOT_ENOUGH,
                            "Cannot release more than reserved count");
                }
            }
        }
    }

    private StockMovement recordMovement(Inventory inventory, StockMovementType type, int quantity,
                                          int beforeOnHand, int beforeReserved, int beforeAvailable,
                                          int afterOnHand, int afterReserved, int afterAvailable,
                                          String note, String createdBy) {
        return recordMovement(inventory, type, quantity,
                beforeOnHand, beforeReserved, beforeAvailable,
                afterOnHand, afterReserved, afterAvailable,
                note, createdBy, null, null);
    }

    private StockMovement recordMovement(Inventory inventory, StockMovementType type, int quantity,
                                          int beforeOnHand, int beforeReserved, int beforeAvailable,
                                          int afterOnHand, int afterReserved, int afterAvailable,
                                          String note, String createdBy,
                                          String referenceType, String referenceId) {
        StockMovement movement = new StockMovement();
        movement.setVariant(inventory.getVariant());
        movement.setWarehouse(inventory.getWarehouse());
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setNote(note);
        movement.setBeforeOnHand(beforeOnHand);
        movement.setBeforeReserved(beforeReserved);
        movement.setBeforeAvailable(beforeAvailable);
        movement.setAfterOnHand(afterOnHand);
        movement.setAfterReserved(afterReserved);
        movement.setAfterAvailable(afterAvailable);
        return stockMovementRepository.save(movement);
    }

}
