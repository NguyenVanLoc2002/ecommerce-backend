package com.locnguyen.ecommerce.domains.carrier.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierConnectionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_carrier_configs")
@Getter
@Setter
@NoArgsConstructor
public class CarrierConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrier_id", nullable = false, unique = true)
    private Carrier carrier;

    @Column(name = "api_key_enc", length = 1000)
    private String apiKeyEnc;

    @Column(name = "secret_key_enc", length = 1000)
    private String secretKeyEnc;

    @Column(name = "webhook_secret_enc", length = 1000)
    private String webhookSecretEnc;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "provider_account_id", length = 100)
    private String providerAccountId;

    @Column(name = "provider_account_phone", length = 50)
    private String providerAccountPhone;

    @Column(name = "provider_brand_name", length = 200)
    private String providerBrandName;

    @Column(name = "pickup_address", length = 500)
    private String pickupAddress;

    @Column(name = "pickup_short_address", length = 255)
    private String pickupShortAddress;

    @Column(name = "pickup_name", length = 200)
    private String pickupName;

    @Column(name = "pickup_phone", length = 50)
    private String pickupPhone;

    @Column(name = "pickup_lat", precision = 10, scale = 7)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", precision = 10, scale = 7)
    private BigDecimal pickupLng;

    @Column(name = "default_service_code", length = 100)
    private String defaultServiceCode;

    @Column(name = "default_payment_method", length = 50)
    private String defaultPaymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", length = 50)
    private CarrierConnectionStatus connectionStatus;

    @Column(name = "last_health_check_at")
    private LocalDateTime lastHealthCheckAt;

    @Column(name = "last_health_check_error", length = 1000)
    private String lastHealthCheckError;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;
}
