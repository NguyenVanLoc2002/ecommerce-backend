package com.locnguyen.ecommerce.domains.returns.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public record ReturnLine(
        @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "order_item_id", nullable = false, length = 36) UUID orderItemId,
        @Column(name = "quantity", nullable = false) int quantity) {}
