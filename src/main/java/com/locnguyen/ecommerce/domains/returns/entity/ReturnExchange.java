package com.locnguyen.ecommerce.domains.returns.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.returns.enums.ExchangeStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "return_exchanges")
@Getter
@Setter
public class ReturnExchange extends BaseEntity {
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "return_id", length = 36, nullable = false)
    private UUID returnId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "order_item_id", length = 36, nullable = false)
    private UUID orderItemId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "variant_id", length = 36, nullable = false)
    private UUID variantId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "warehouse_id", length = 36, nullable = false)
    private UUID warehouseId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "request_key", length = 100, nullable = false)
    private String requestKey;

    @Column(name = "actor", length = 255, nullable = false)
    private String actor;

    @Column(name = "historical_price_evidence", length = 500)
    private String historicalPriceEvidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40, nullable = false)
    private ExchangeStatus status;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "replacement_order_id", length = 36)
    private UUID replacementOrderId;

    @Column(name = "cancelled_by", length = 255)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
