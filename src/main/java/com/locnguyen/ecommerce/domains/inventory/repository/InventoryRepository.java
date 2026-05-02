package com.locnguyen.ecommerce.domains.inventory.repository;

import com.locnguyen.ecommerce.domains.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import java.util.UUID;
public interface InventoryRepository extends
        JpaRepository<Inventory, UUID>,
        JpaSpecificationExecutor<Inventory> {

    Optional<Inventory> findByVariantIdAndWarehouseId(UUID variantId, UUID warehouseId);

    List<Inventory> findByVariantId(UUID variantId);

    /**
     * Batch-load inventories for multiple variants in one query.
     * Use this in order creation to avoid N+1 selects per cart item.
     */
    @Query("""
            SELECT i
            FROM Inventory i
            JOIN FETCH i.warehouse w
            JOIN i.variant v
            JOIN v.product p
            WHERE v.id IN :variantIds
              AND v.deleted = false
              AND p.deleted = false
              AND w.deleted = false
            """)
    List<Inventory> findByVariantIdIn(@Param("variantIds") List<UUID> variantIds);

    List<Inventory> findByWarehouseId(UUID warehouseId);

    /**
     * Sum available stock across all warehouses for a given variant.
     * Used by cart to validate quantity before add/update.
     */
    @Query("""
            SELECT COALESCE(SUM(i.onHand - i.reserved), 0)
            FROM Inventory i
            JOIN i.warehouse w
            JOIN i.variant v
            JOIN v.product p
            WHERE v.id = :variantId
              AND v.deleted = false
              AND p.deleted = false
              AND w.deleted = false
            """)
    int sumAvailableByVariantId(@Param("variantId") UUID variantId);

    /**
     * Atomically increase on_hand (for IMPORT and RETURN operations).
     *
     * @return number of rows updated (0 or 1)
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.onHand = i.onHand + :quantity WHERE i.id = :id")
    int increaseOnHand(@Param("id") UUID id, @Param("quantity") int quantity);

    /**
     * Atomically decrease on_hand (for EXPORT and COMPLETE ORDER operations).
     * Prevents going below reserved count.
     *
     * @return number of rows updated (0 or 1)
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.onHand = i.onHand - :quantity " +
            "WHERE i.id = :id AND i.onHand - :quantity >= i.reserved")
    int decreaseOnHand(@Param("id") UUID id, @Param("quantity") int quantity);

    /**
     * Atomically increase reserved (for RESERVE/CHECKOUT operations).
     * Prevents oversell by checking available >= quantity.
     *
     * @return number of rows updated (0 or 1)
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.reserved = i.reserved + :quantity " +
            "WHERE i.id = :id AND (i.onHand - i.reserved) >= :quantity")
    int increaseReserved(@Param("id") UUID id, @Param("quantity") int quantity);

    /**
     * Atomically decrease reserved (for RELEASE on cancel).
     * Prevents reserved going below zero.
     *
     * @return number of rows updated (0 or 1)
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.reserved = i.reserved - :quantity " +
            "WHERE i.id = :id AND i.reserved >= :quantity")
    int decreaseReserved(@Param("id") UUID id, @Param("quantity") int quantity);

    /**
     * Atomically decrease both on_hand and reserved (for ORDER COMPLETE).
     * Checks available >= quantity.
     *
     * @return number of rows updated (0 or 1)
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.onHand = i.onHand - :quantity, i.reserved = i.reserved - :quantity " +
            "WHERE i.id = :id AND (i.onHand - i.reserved) >= :quantity AND i.reserved >= :quantity")
    int decreaseOnHandAndReserved(@Param("id") UUID id, @Param("quantity") int quantity);

    /**
     * Pessimistic lock read for critical sections.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.id = :id")
    Optional<Inventory> findByIdWithLock(@Param("id") UUID id);

    /**
     * Pessimistic lock by variant + warehouse.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variant.id = :variantId AND i.warehouse.id = :warehouseId")
    Optional<Inventory> findByVariantIdAndWarehouseIdWithLock(
            @Param("variantId") UUID variantId, @Param("warehouseId") UUID warehouseId);
}
