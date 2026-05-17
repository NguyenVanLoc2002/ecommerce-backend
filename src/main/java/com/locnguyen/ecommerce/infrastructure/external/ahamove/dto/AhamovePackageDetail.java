package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AhamovePackageDetail(
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        String description
) {}
