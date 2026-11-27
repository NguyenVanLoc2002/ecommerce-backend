package com.locnguyen.ecommerce.domains.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDeliverySla {
    @Column(name = "delivery_sla_service_code", length = 100)
    private String serviceCode;

    @Column(name = "delivery_sla_min_business_days")
    private Integer minBusinessDays;

    @Column(name = "delivery_sla_max_business_days")
    private Integer maxBusinessDays;

    public OrderDeliverySla(String serviceCode, int minBusinessDays, int maxBusinessDays) {
        this.serviceCode = serviceCode;
        this.minBusinessDays = minBusinessDays;
        this.maxBusinessDays = maxBusinessDays;
    }
}
