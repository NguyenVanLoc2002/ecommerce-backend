package com.locnguyen.ecommerce.domains.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.locnguyen.ecommerce.domains.order.service.OrderHoldService;
import java.time.LocalDateTime;

/**
 * Scheduled tasks for inventory maintenance.
 * Auto-releases expired reservations to return stock to available pool.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryScheduler {

    private final InventoryService inventoryService;
    private final OrderHoldService orderHoldService;

    /**
     * Run every 5 minutes to check for and release expired reservations.
     */
    @Scheduled(fixedDelayString = "${app.inventory.expired-check-interval:300000}")
    public void releaseExpiredReservations() {
        for (String orderCode : inventoryService.findExpiredOrderReferences(LocalDateTime.now())) {
            try {
                orderHoldService.expireOrder(orderCode);
            } catch (Exception e) {
                log.error("Scheduled order hold expiry failed: orderCode={}", orderCode, e);
            }
        }
        try {
            int released = inventoryService.releaseExpiredReservations();
            if (released > 0) {
                log.info("Scheduled task: auto-released {} expired reservations", released);
            }
        } catch (Exception e) {
            log.error("Scheduled task failed: releaseExpiredReservations", e);
        }
    }
}
