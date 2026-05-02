package com.locnguyen.ecommerce.domains.promotion.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

import java.util.UUID;
@Getter
@Setter
public class VoucherFilter {
    private String code;
    private UUID promotionId;
    private Boolean active;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Boolean isDeleted;
    private Boolean includeDeleted;
}
