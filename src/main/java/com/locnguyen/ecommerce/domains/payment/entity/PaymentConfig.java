package com.locnguyen.ecommerce.domains.payment.entity;

import com.locnguyen.ecommerce.common.auditing.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "payment_configs")
@Getter
@Setter
@NoArgsConstructor
public class PaymentConfig extends BaseEntity {

    @Column(name = "provider", length = 50, nullable = false, unique = true)
    private String provider;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "environment", length = 50)
    private String environment;

    @Column(name = "momo_partner_code_enc", length = 1000)
    private String momoPartnerCodeEnc;

    @Column(name = "momo_access_key_enc", length = 1000)
    private String momoAccessKeyEnc;

    @Column(name = "momo_secret_key_enc", length = 1000)
    private String momoSecretKeyEnc;

    @Column(name = "momo_create_url", length = 500)
    private String momoCreateUrl;

    @Column(name = "momo_redirect_url", length = 500)
    private String momoRedirectUrl;

    @Column(name = "momo_ipn_url", length = 500)
    private String momoIpnUrl;

    @Column(name = "momo_request_type", length = 100)
    private String momoRequestType;

    @Column(name = "momo_lang", length = 20)
    private String momoLang;

    @Column(name = "paypal_client_id_enc", length = 1000)
    private String paypalClientIdEnc;

    @Column(name = "paypal_client_secret_enc", length = 1000)
    private String paypalClientSecretEnc;

    @Column(name = "paypal_base_url", length = 500)
    private String paypalBaseUrl;

    @Column(name = "paypal_return_url", length = 500)
    private String paypalReturnUrl;

    @Column(name = "paypal_cancel_url", length = 500)
    private String paypalCancelUrl;

    @Column(name = "paypal_webhook_id", length = 255)
    private String paypalWebhookId;

    @Column(name = "paypal_currency", length = 20)
    private String paypalCurrency;

    @Column(name = "paypal_brand_name", length = 200)
    private String paypalBrandName;

    @Column(name = "paypal_locale", length = 50)
    private String paypalLocale;

    @Column(name = "paypal_user_action", length = 100)
    private String paypalUserAction;

    @Column(name = "paypal_payment_method_preference", length = 100)
    private String paypalPaymentMethodPreference;

    @Column(name = "paypal_shipping_preference", length = 100)
    private String paypalShippingPreference;

    @Column(name = "paypal_test_conversion_enabled", nullable = false)
    private boolean paypalTestConversionEnabled;

    @Column(name = "paypal_test_conversion_rate_vnd_usd", precision = 18, scale = 6)
    private BigDecimal paypalTestConversionRateVndUsd;

    @Column(name = "paypal_conversion_rate_vnd_usd", precision = 18, scale = 6)
    private BigDecimal paypalConversionRateVndUsd;

    @Column(name = "connect_timeout_ms")
    private Integer connectTimeoutMs;

    @Column(name = "read_timeout_ms")
    private Integer readTimeoutMs;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;
}
