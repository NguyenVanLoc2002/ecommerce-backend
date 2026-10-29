package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record AhamovePackageDetail(
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        String description
) {}
