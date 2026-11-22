package com.locnguyen.ecommerce.domains.shipment.service.impl;

import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** Records successful manual shipment actions only after their transaction commits. */
final class ShipmentAuditAfterCommit {

    private ShipmentAuditAfterCommit() {}

    static void log(AuditLogService auditLogService, AuditAction action, UUID shipmentId,
                    String actor, String details) {
        Runnable writeAudit = () -> auditLogService.logWithActor(
                action, "SHIPMENT", shipmentId.toString(), actor, details);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            writeAudit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                writeAudit.run();
            }
        });
    }
}
