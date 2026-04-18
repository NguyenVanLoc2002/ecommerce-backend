package com.locnguyen.ecommerce.domains.inventory.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.inventory.dto.ReserveStockRequest;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InventoryService}.
 *
 * Tests cover:
 * - reserveStock: available check, atomic update, reservation record creation
 * - releaseStock: no-op on empty, marks reservations RELEASED
 * - completeOrder: decreases both on_hand and reserved
 * - importStock / exportStock: basic increment/decrement + audit trail
 * - releaseExpiredReservations: delegates per-variant release
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryReservationRepository reservationRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock WarehouseService warehouseService;
    @Mock ProductVariantRepository productVariantRepository;
    @Mock InventoryMapper inventoryMapper;

    @InjectMocks InventoryService inventoryService;

    // ─── factories ───────────────────────────────────────────────────────────

    private ProductVariant variant(long id) {
        ProductVariant v = new ProductVariant();
        setId(v, id);
        return v;
    }

    private Warehouse warehouse(long id) {
        Warehouse w = new Warehouse();
        setId(w, id);
        w.setName("Warehouse " + id);
        w.setCode("WH-" + id);
        return w;
    }

    private Inventory inventory(long id, ProductVariant variant, Warehouse warehouse,
                                 int onHand, int reserved) {
        Inventory inv = new Inventory();
        setId(inv, id);
        inv.setVariant(variant);
        inv.setWarehouse(warehouse);
        inv.setOnHand(onHand);
        inv.setReserved(reserved);
        return inv;
    }

    private InventoryReservation pendingReservation(long id, ProductVariant variant,
                                                     Warehouse warehouse, int quantity) {
        InventoryReservation r = new InventoryReservation();
        setId(r, id);
        r.setVariant(variant);
        r.setWarehouse(warehouse);
        r.setQuantity(quantity);
        r.setStatus(ReservationStatus.PENDING);
        r.setReferenceType("ORDER");
        r.setReferenceId("ORD202604060001");
        r.setExpiresAt(LocalDateTime.now().plusHours(24));
        return r;
    }

    private ReserveStockRequest reserveRequest(Long variantId, Long warehouseId, int quantity) {
        ReserveStockRequest req = new ReserveStockRequest();
        req.setVariantId(variantId);
        req.setWarehouseId(warehouseId);
        req.setQuantity(quantity);
        req.setReferenceType("ORDER");
        req.setReferenceId("ORD202604060001");
        req.setExpiresAt(LocalDateTime.now().plusHours(24));
        return req;
    }

    private static void setId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    // ─── reserveStock ─────────────────────────────────────────────────────────

    @Nested
    class ReserveStock {

        @Test
        void throws_PRODUCT_VARIANT_NOT_FOUND_when_variant_missing() {
            when(productVariantRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> inventoryService.reserveStock(reserveRequest(99L, 1L, 2)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_VARIANT_NOT_FOUND);
        }

        @Test
        void throws_INVENTORY_NOT_FOUND_when_no_record_for_variant_warehouse() {
            when(productVariantRepository.existsById(1L)).thenReturn(true);
            when(warehouseService.findOrThrow(1L)).thenReturn(warehouse(1L));
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.reserveStock(reserveRequest(1L, 1L, 2)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND);
        }

        @Test
        void throws_INVENTORY_NOT_ENOUGH_when_available_is_less_than_requested() {
            // onHand=5, reserved=4 → available=1, requesting 2
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 5, 4);

            when(productVariantRepository.existsById(1L)).thenReturn(true);
            when(warehouseService.findOrThrow(1L)).thenReturn(w);
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv));

            assertThatThrownBy(() -> inventoryService.reserveStock(reserveRequest(1L, 1L, 2)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_ENOUGH);
        }

        @Test
        void throws_STOCK_RESERVATION_FAILED_when_atomic_update_returns_zero() {
            // available=10 but the DB update fails (race condition simulation)
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 15, 5);
            Inventory refreshed = inventory(10L, v, w, 15, 5);

            when(productVariantRepository.existsById(1L)).thenReturn(true);
            when(warehouseService.findOrThrow(1L)).thenReturn(w);
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.increaseReserved(10L, 5)).thenReturn(0); // atomic check fails

            assertThatThrownBy(() -> inventoryService.reserveStock(reserveRequest(1L, 1L, 5)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.STOCK_RESERVATION_FAILED);
        }

        @Test
        void happy_path_creates_reservation_record_and_records_movement() {
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 20, 0);
            Inventory refreshed = inventory(10L, v, w, 20, 5);

            when(productVariantRepository.existsById(1L)).thenReturn(true);
            when(warehouseService.findOrThrow(1L)).thenReturn(w);
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.increaseReserved(10L, 5)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            InventoryReservation result = inventoryService.reserveStock(reserveRequest(1L, 1L, 5));

            assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);
            assertThat(result.getQuantity()).isEqualTo(5);
            assertThat(result.getReferenceType()).isEqualTo("ORDER");

            // Movement audit should record RESERVE type
            verify(stockMovementRepository).save(argThat(m ->
                    m.getMovementType() == StockMovementType.RESERVE
                    && m.getQuantity() == 5));
        }

        @Test
        void reservation_expires_at_set_time_is_stored() {
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 20, 0);
            Inventory refreshed = inventory(10L, v, w, 20, 3);
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

            when(productVariantRepository.existsById(1L)).thenReturn(true);
            when(warehouseService.findOrThrow(1L)).thenReturn(w);
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.increaseReserved(10L, 3)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ReserveStockRequest req = reserveRequest(1L, 1L, 3);
            req.setExpiresAt(expiresAt);

            InventoryReservation result = inventoryService.reserveStock(req);

            assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
        }

        @Test
        void can_reserve_exact_available_amount() {
            // onHand=10, reserved=7 → available=3, requesting exactly 3
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 10, 7);
            Inventory refreshed = inventory(10L, v, w, 10, 10);

            when(productVariantRepository.existsById(1L)).thenReturn(true);
            when(warehouseService.findOrThrow(1L)).thenReturn(w);
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.increaseReserved(10L, 3)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // Must not throw
            InventoryReservation result = inventoryService.reserveStock(reserveRequest(1L, 1L, 3));

            assertThat(result).isNotNull();
        }
    }

    // ─── releaseStock ─────────────────────────────────────────────────────────

    @Nested
    class ReleaseStock {

        @Test
        void no_op_when_no_pending_reservations_found() {
            when(reservationRepository.findByReferenceTypeAndReferenceIdAndStatus(
                    "ORDER", "ORD202604060001", ReservationStatus.PENDING))
                    .thenReturn(List.of());

            inventoryService.releaseStock("ORDER", "ORD202604060001");

            verify(inventoryRepository, never()).decreaseReserved(anyLong(), anyInt());
        }

        @Test
        void decreases_reserved_for_each_pending_reservation() {
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 20, 5);
            Inventory refreshed = inventory(10L, v, w, 20, 0);
            InventoryReservation res = pendingReservation(1L, v, w, 5);

            when(reservationRepository.findByReferenceTypeAndReferenceIdAndStatus(
                    "ORDER", "ORD202604060001", ReservationStatus.PENDING))
                    .thenReturn(List.of(res));
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.decreaseReserved(10L, 5)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.releaseStock("ORDER", "ORD202604060001");

            verify(inventoryRepository).decreaseReserved(10L, 5);
        }

        @Test
        void reservation_is_marked_RELEASED_after_release() {
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 20, 5);
            Inventory refreshed = inventory(10L, v, w, 20, 0);
            InventoryReservation res = pendingReservation(1L, v, w, 5);

            when(reservationRepository.findByReferenceTypeAndReferenceIdAndStatus(
                    "ORDER", "ORD202604060001", ReservationStatus.PENDING))
                    .thenReturn(List.of(res));
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.decreaseReserved(10L, 5)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.releaseStock("ORDER", "ORD202604060001");

            verify(reservationRepository).save(argThat(
                    r -> r.getStatus() == ReservationStatus.RELEASED));
        }

        @Test
        void releases_all_reservations_for_multi_item_order() {
            ProductVariant v1 = variant(1L);
            ProductVariant v2 = variant(2L);
            Warehouse w = warehouse(1L);
            Inventory inv1 = inventory(10L, v1, w, 20, 3);
            Inventory inv2 = inventory(11L, v2, w, 15, 2);
            Inventory refreshed1 = inventory(10L, v1, w, 20, 0);
            Inventory refreshed2 = inventory(11L, v2, w, 15, 0);
            InventoryReservation res1 = pendingReservation(1L, v1, w, 3);
            InventoryReservation res2 = pendingReservation(2L, v2, w, 2);

            when(reservationRepository.findByReferenceTypeAndReferenceIdAndStatus(
                    "ORDER", "ORD202604060001", ReservationStatus.PENDING))
                    .thenReturn(List.of(res1, res2));
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv1));
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(2L, 1L))
                    .thenReturn(Optional.of(inv2));
            when(inventoryRepository.decreaseReserved(10L, 3)).thenReturn(1);
            when(inventoryRepository.decreaseReserved(11L, 2)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed1));
            when(inventoryRepository.findById(11L)).thenReturn(Optional.of(refreshed2));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.releaseStock("ORDER", "ORD202604060001");

            verify(inventoryRepository).decreaseReserved(10L, 3);
            verify(inventoryRepository).decreaseReserved(11L, 2);
            verify(reservationRepository, times(2)).save(any());
        }
    }

    // ─── completeOrder ─────────────────────────────────────────────────────────

    @Nested
    class CompleteOrder {

        @Test
        void no_op_when_no_pending_reservations() {
            when(reservationRepository.findByReferenceTypeAndReferenceIdAndStatus(
                    "ORDER", "ORD202604060001", ReservationStatus.PENDING))
                    .thenReturn(List.of());

            inventoryService.completeOrder("ORDER", "ORD202604060001");

            verify(inventoryRepository, never()).decreaseOnHandAndReserved(anyLong(), anyInt());
        }

        @Test
        void decreases_both_onHand_and_reserved_on_completion() {
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 20, 5);
            Inventory refreshed = inventory(10L, v, w, 15, 0);
            InventoryReservation res = pendingReservation(1L, v, w, 5);

            when(reservationRepository.findByReferenceTypeAndReferenceIdAndStatus(
                    "ORDER", "ORD202604060001", ReservationStatus.PENDING))
                    .thenReturn(List.of(res));
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.decreaseOnHandAndReserved(10L, 5)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.completeOrder("ORDER", "ORD202604060001");

            verify(inventoryRepository).decreaseOnHandAndReserved(10L, 5);
            // Reservation should be marked RELEASED
            verify(reservationRepository).save(argThat(
                    r -> r.getStatus() == ReservationStatus.RELEASED));
        }

        @Test
        void records_EXPORT_movement_on_order_completion() {
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 20, 5);
            Inventory refreshed = inventory(10L, v, w, 15, 0);
            InventoryReservation res = pendingReservation(1L, v, w, 5);

            when(reservationRepository.findByReferenceTypeAndReferenceIdAndStatus(
                    "ORDER", "ORD202604060001", ReservationStatus.PENDING))
                    .thenReturn(List.of(res));
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.decreaseOnHandAndReserved(10L, 5)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.completeOrder("ORDER", "ORD202604060001");

            verify(stockMovementRepository).save(argThat(
                    m -> m.getMovementType() == StockMovementType.EXPORT));
        }
    }

    // ─── importStock ─────────────────────────────────────────────────────────

    @Nested
    class ImportStock {

        @Test
        void throws_PRODUCT_VARIANT_NOT_FOUND_when_variant_missing() {
            when(productVariantRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() ->
                    inventoryService.importStock(99L, 1L, 10, "Initial import"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_VARIANT_NOT_FOUND);
        }

        @Test
        void creates_new_inventory_record_if_none_exists() {
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory newInv = inventory(10L, v, w, 0, 0);
            Inventory refreshed = inventory(10L, v, w, 50, 0);

            when(productVariantRepository.existsById(1L)).thenReturn(true);
            when(warehouseService.findOrThrow(1L)).thenReturn(w);
            // First call: not found → getOrCreateInventory creates it
            when(inventoryRepository.findByVariantIdAndWarehouseId(1L, 1L))
                    .thenReturn(Optional.empty());
            when(productVariantRepository.findById(1L)).thenReturn(Optional.of(v));
            when(inventoryRepository.save(any())).thenReturn(newInv);
            when(inventoryRepository.increaseOnHand(10L, 50)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.importStock(1L, 1L, 50, "First import");

            verify(inventoryRepository).increaseOnHand(10L, 50);
        }

        @Test
        void records_IMPORT_movement_with_correct_before_after_values() {
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 20, 3);
            Inventory refreshed = inventory(10L, v, w, 30, 3);

            when(productVariantRepository.existsById(1L)).thenReturn(true);
            when(warehouseService.findOrThrow(1L)).thenReturn(w);
            when(inventoryRepository.findByVariantIdAndWarehouseId(1L, 1L))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.increaseOnHand(10L, 10)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.importStock(1L, 1L, 10, "Restock");

            verify(stockMovementRepository).save(argThat(m ->
                    m.getMovementType() == StockMovementType.IMPORT
                    && m.getQuantity() == 10
                    && m.getBeforeOnHand() == 20
                    && m.getAfterOnHand() == 30));
        }
    }

    // ─── exportStock ─────────────────────────────────────────────────────────

    @Nested
    class ExportStock {

        @Test
        void throws_INVENTORY_NOT_ENOUGH_when_would_go_below_reserved() {
            // DB guard returns 0 (would breach reserved floor)
            ProductVariant v = variant(1L);
            Warehouse w = warehouse(1L);
            Inventory inv = inventory(10L, v, w, 5, 5);

            when(productVariantRepository.existsById(1L)).thenReturn(true);
            when(warehouseService.findOrThrow(1L)).thenReturn(w);
            when(inventoryRepository.findByVariantIdAndWarehouseId(1L, 1L))
                    .thenReturn(Optional.of(inv));
            when(inventoryRepository.decreaseOnHand(10L, 1)).thenReturn(0); // blocked by DB check

            assertThatThrownBy(() ->
                    inventoryService.exportStock(1L, 1L, 1, "Manual export"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_ENOUGH);
        }

        @Test
        void throws_INVENTORY_NOT_FOUND_when_no_inventory_record() {
            when(productVariantRepository.existsById(1L)).thenReturn(true);
            when(warehouseService.findOrThrow(1L)).thenReturn(warehouse(1L));
            when(inventoryRepository.findByVariantIdAndWarehouseId(1L, 1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    inventoryService.exportStock(1L, 1L, 5, "Export"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND);
        }
    }

    // ─── releaseExpiredReservations ───────────────────────────────────────────

    @Nested
    class ReleaseExpiredReservations {

        @Test
        void returns_zero_when_no_expired_reservations() {
            when(reservationRepository.findExpiredReservations(any())).thenReturn(List.of());

            int released = inventoryService.releaseExpiredReservations();

            assertThat(released).isEqualTo(0);
        }

        @Test
        void returns_count_of_successfully_released_reservations() {
            ProductVariant v1 = variant(1L);
            ProductVariant v2 = variant(2L);
            Warehouse w = warehouse(1L);

            InventoryReservation r1 = pendingReservation(1L, v1, w, 3);
            InventoryReservation r2 = pendingReservation(2L, v2, w, 2);

            when(reservationRepository.findExpiredReservations(any()))
                    .thenReturn(List.of(r1, r2));

            // For each releaseStockForVariant call, stub the inner reservation lookup
            when(reservationRepository.findByReferenceTypeAndReferenceIdAndVariantIdAndStatus(
                    eq("ORDER"), eq("ORD202604060001"), eq(1L), eq(ReservationStatus.PENDING)))
                    .thenReturn(Optional.of(r1));
            when(reservationRepository.findByReferenceTypeAndReferenceIdAndVariantIdAndStatus(
                    eq("ORDER"), eq("ORD202604060001"), eq(2L), eq(ReservationStatus.PENDING)))
                    .thenReturn(Optional.of(r2));

            Inventory inv1 = inventory(10L, v1, w, 10, 3);
            Inventory inv2 = inventory(11L, v2, w, 10, 2);
            Inventory refreshed1 = inventory(10L, v1, w, 10, 0);
            Inventory refreshed2 = inventory(11L, v2, w, 10, 0);

            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(1L, 1L))
                    .thenReturn(Optional.of(inv1));
            when(inventoryRepository.findByVariantIdAndWarehouseIdWithLock(2L, 1L))
                    .thenReturn(Optional.of(inv2));
            when(inventoryRepository.decreaseReserved(10L, 3)).thenReturn(1);
            when(inventoryRepository.decreaseReserved(11L, 2)).thenReturn(1);
            when(inventoryRepository.findById(10L)).thenReturn(Optional.of(refreshed1));
            when(inventoryRepository.findById(11L)).thenReturn(Optional.of(refreshed2));
            when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            int released = inventoryService.releaseExpiredReservations();

            assertThat(released).isEqualTo(2);
        }
    }
}
