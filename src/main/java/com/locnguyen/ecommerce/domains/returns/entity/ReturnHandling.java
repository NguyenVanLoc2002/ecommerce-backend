package com.locnguyen.ecommerce.domains.returns.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.returns.enums.ReturnStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "return_handling")
@Getter
@Setter
public class ReturnHandling extends BaseEntity {
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "return_id", nullable = false, length = 36)
    private UUID returnId;

    @Column(name = "request_key", nullable = false, length = 100)
    private String requestKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 40)
    private ReturnStatus nextStatus;

    @Column(name = "actor", nullable = false, length = 255)
    private String actor;

    @Column(name = "note", nullable = false, length = 1000)
    private String note;

    @Column(name = "carrier", length = 100)
    private String carrier;

    @Column(name = "tracking_number", length = 200)
    private String trackingNumber;

    @Column(name = "tracking_at")
    private LocalDateTime trackingAt;

    @Column(name = "evidence_reference", length = 500)
    private String evidenceReference;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "warehouse_id", length = 36)
    private UUID warehouseId;

    @ElementCollection
    @CollectionTable(name = "return_handling_lines", joinColumns = @JoinColumn(name = "handling_id"))
    private List<ReturnHandlingLine> items = new ArrayList<>();
}
