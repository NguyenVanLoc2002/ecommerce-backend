package com.locnguyen.ecommerce.domains.promotion.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PromotionFilter {
    private String name;
    private String scope;
    private Boolean active;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}
