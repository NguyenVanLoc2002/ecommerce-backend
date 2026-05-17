package com.locnguyen.ecommerce.domains.carrier.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "carriers")
@Getter
@Setter
@NoArgsConstructor
public class Carrier extends BaseEntity {

    @Column(name = "code", length = 50, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", length = 100, nullable = false)
    private CarrierProviderType providerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private CarrierStatus status = CarrierStatus.ACTIVE;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "description", length = 500)
    private String description;

    @OneToOne(mappedBy = "carrier", fetch = FetchType.LAZY)
    private CarrierConfig config;
}
