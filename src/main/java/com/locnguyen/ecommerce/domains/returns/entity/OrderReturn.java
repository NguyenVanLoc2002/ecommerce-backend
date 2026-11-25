package com.locnguyen.ecommerce.domains.returns.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnReason;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_returns")
@Getter
@Setter
public class OrderReturn extends BaseEntity {
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "order_id", nullable = false, length = 36)
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "submitted_by_customer_id", nullable = false, updatable = false, length = 36)
    private UUID submittedByCustomerId;

    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "request_key", nullable = false, length = 100)
    private String requestKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private ReturnReason reason;

    @Column(name = "customer_note", nullable = false, length = 1000)
    private String customerNote;

    @Column(name = "original_condition", nullable = false)
    private boolean originalCondition;

    @Column(name = "unopened", nullable = false)
    private boolean unopened;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ReturnStatus status;

    @BatchSize(size = 32)
    @ElementCollection
    @CollectionTable(name = "order_return_lines", joinColumns = @JoinColumn(name = "return_id"))
    private List<ReturnLine> lines = new ArrayList<>();
}
