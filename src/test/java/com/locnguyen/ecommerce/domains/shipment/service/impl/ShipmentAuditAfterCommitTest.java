package com.locnguyen.ecommerce.domains.shipment.service.impl;

import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ShipmentAuditAfterCommitTest {

    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final UUID shipmentId = UUID.randomUUID();
    private final TransactionTemplate transaction = new TransactionTemplate(new TestTransactionManager());

    @Test
    void log_whenTransactionCommits_recordsCapturedActorAndDetailsAfterCommit() {
        transaction.executeWithoutResult(status -> {
            recordManualAudit();
            verifyNoInteractions(auditLogService);
        });

        verify(auditLogService).logWithActor(AuditAction.SHIPMENT_STATUS_UPDATED, "SHIPMENT",
                shipmentId.toString(), "admin@example.test", "source=MANUAL; status=IN_TRANSIT");
    }

    @Test
    void log_whenTransactionRollsBack_doesNotRecordSuccessfulAction() {
        transaction.executeWithoutResult(status -> {
            recordManualAudit();
            status.setRollbackOnly();
        });

        verifyNoInteractions(auditLogService);
    }

    @Test
    void log_withoutTransaction_recordsActionImmediately() {
        recordManualAudit();

        verify(auditLogService).logWithActor(AuditAction.SHIPMENT_STATUS_UPDATED, "SHIPMENT",
                shipmentId.toString(), "admin@example.test", "source=MANUAL; status=IN_TRANSIT");
    }

    private void recordManualAudit() {
        ShipmentAuditAfterCommit.log(auditLogService, AuditAction.SHIPMENT_STATUS_UPDATED,
                shipmentId, "admin@example.test", "source=MANUAL; status=IN_TRANSIT");
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
