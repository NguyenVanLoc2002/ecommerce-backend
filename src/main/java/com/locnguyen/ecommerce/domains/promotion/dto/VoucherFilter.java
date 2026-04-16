package com.locnguyen.ecommerce.domains.promotion.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class VoucherFilter {
    private String code;
    private Long promotionId;
    private Boolean active;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
