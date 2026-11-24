package com.locnguyen.ecommerce.domains.returns.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "return_decisions")
@Getter
@Setter
public class ReturnDecision extends BaseEntity {
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "return_id", nullable = false, updatable = false, length = 36)
    private UUID returnId;

    @Column(name = "request_key", nullable = false, updatable = false, length = 100)
    private String requestKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, updatable = false, length = 40)
    private ReturnStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, updatable = false, length = 40)
    private ReturnStatus nextStatus;

    @Column(name = "actor", nullable = false, updatable = false, length = 255)
    private String actor;

    @Column(name = "reason", nullable = false, updatable = false, length = 1000)
    private String reason;

    @Column(name = "verified_delivered_at", updatable = false)
    private LocalDateTime verifiedDeliveredAt;
}
