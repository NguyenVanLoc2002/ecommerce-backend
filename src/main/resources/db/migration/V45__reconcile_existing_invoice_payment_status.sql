-- Reconcile previously issued invoices against persisted settlement/refund events.
-- Order and invoice snapshots are intentionally left unchanged.
UPDATE invoices i
JOIN orders o ON o.id = i.order_id
SET i.status = CASE
        WHEN o.payment_status = 'REFUNDED' THEN 'REFUNDED'
        WHEN o.payment_status = 'PARTIALLY_REFUNDED' THEN 'PARTIALLY_REFUNDED'
        WHEN o.payment_status = 'PAID' THEN 'PAID'
        WHEN o.status = 'CANCELLED'
             AND o.payment_status IN ('PENDING', 'FAILED', 'CANCELLED') THEN 'VOIDED'
        ELSE i.status
    END,
    i.updated_at = UTC_TIMESTAMP(),
    i.updated_by = 'system'
WHERE i.status = 'ISSUED'
  AND (
      o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')
      OR (
          o.status = 'CANCELLED'
          AND o.payment_status IN ('PENDING', 'FAILED', 'CANCELLED')
      )
  );
