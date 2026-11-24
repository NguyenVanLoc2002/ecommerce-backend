package com.locnguyen.ecommerce.domains.returns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public record ReturnHandlingLine(
        @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "order_item_id", length = 36, nullable = false) UUID orderItemId,
        @Column(name = "quantity", nullable = false) int quantity,
        @Column(name = "restock", nullable = false) int restock,
        @Column(name = "damaged", nullable = false) int damaged) {}
