package com.locnguyen.ecommerce.domains.payment.service.impl;

import com.locnguyen.ecommerce.common.config.payment.PaymentProperties;
import com.locnguyen.ecommerce.common.security.PaymentConfigCipher;
import com.locnguyen.ecommerce.domains.payment.config.MomoResolvedPaymentConfig;
import com.locnguyen.ecommerce.domains.payment.config.PaymentProviderConfigResolver;
import com.locnguyen.ecommerce.domains.payment.config.PaypalResolvedPaymentConfig;
import com.locnguyen.ecommerce.domains.payment.dto.MomoPaymentConfigRequest;
import com.locnguyen.ecommerce.domains.payment.dto.MomoPaymentConfigResponse;
import com.locnguyen.ecommerce.domains.payment.dto.PaypalPaymentConfigRequest;
import com.locnguyen.ecommerce.domains.payment.dto.PaypalPaymentConfigResponse;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentConfig;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentConfigRepository;
import com.locnguyen.ecommerce.domains.payment.service.PaymentConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentConfigServiceImpl implements PaymentConfigService {

    private final PaymentConfigRepository paymentConfigRepository;
    private final PaymentProviderConfigResolver paymentProviderConfigResolver;
    private final PaymentConfigCipher paymentConfigCipher;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional(readOnly = true)
    public MomoPaymentConfigResponse getMomoConfig() {
        Optional<PaymentConfig> config = paymentConfigRepository.findByProviderIgnoreCase(PaymentProviderConfigResolver.MOMO);
        return toMomoResponse(config.orElse(null), paymentProviderConfigResolver.resolveMomo(false));
    }

    @Override
    @Transactional
    public MomoPaymentConfigResponse updateMomoConfig(MomoPaymentConfigRequest request) {
        PaymentConfig config = getOrCreate(PaymentProviderConfigResolver.MOMO);

        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (request.getEnvironment() != null) {
            config.setEnvironment(normalizeOptional(request.getEnvironment()));
        }
        if (request.getPartnerCode() != null) {
            config.setMomoPartnerCodeEnc(encryptOrClear(request.getPartnerCode()));
        }
        if (request.getAccessKey() != null) {
            config.setMomoAccessKeyEnc(encryptOrClear(request.getAccessKey()));
        }
        if (request.getSecretKey() != null) {
            config.setMomoSecretKeyEnc(encryptOrClear(request.getSecretKey()));
        }
        if (request.getCreateUrl() != null) {
            config.setMomoCreateUrl(normalizeOptional(request.getCreateUrl()));
        }
        if (request.getRedirectUrl() != null) {
            config.setMomoRedirectUrl(normalizeOptional(request.getRedirectUrl()));
        }
        if (request.getIpnUrl() != null) {
            config.setMomoIpnUrl(normalizeOptional(request.getIpnUrl()));
        }
        if (request.getRequestType() != null) {
            config.setMomoRequestType(normalizeOptional(request.getRequestType()));
        }
        if (request.getLang() != null) {
            config.setMomoLang(normalizeOptional(request.getLang()));
        }
        if (request.getConnectTimeoutMs() != null) {
            config.setConnectTimeoutMs(request.getConnectTimeoutMs());
        }
        if (request.getReadTimeoutMs() != null) {
            config.setReadTimeoutMs(request.getReadTimeoutMs());
        }

        paymentConfigRepository.save(config);
        return toMomoResponse(config, paymentProviderConfigResolver.resolveMomo(config.isEnabled()));
    }

    @Override
    @Transactional(readOnly = true)
    public PaypalPaymentConfigResponse getPaypalConfig() {
        Optional<PaymentConfig> config = paymentConfigRepository.findByProviderIgnoreCase(PaymentProviderConfigResolver.PAYPAL);
        return toPaypalResponse(config.orElse(null), paymentProviderConfigResolver.resolvePaypal(false));
    }

    @Override
    @Transactional
    public PaypalPaymentConfigResponse updatePaypalConfig(PaypalPaymentConfigRequest request) {
        PaymentConfig config = getOrCreate(PaymentProviderConfigResolver.PAYPAL);

        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (request.getEnvironment() != null) {
            config.setEnvironment(normalizeOptional(request.getEnvironment()));
        }
        if (request.getClientId() != null) {
            config.setPaypalClientIdEnc(encryptOrClear(request.getClientId()));
        }
        if (request.getClientSecret() != null) {
            config.setPaypalClientSecretEnc(encryptOrClear(request.getClientSecret()));
        }
        if (request.getBaseUrl() != null) {
            config.setPaypalBaseUrl(normalizeOptional(request.getBaseUrl()));
        }
        if (request.getReturnUrl() != null) {
            config.setPaypalReturnUrl(normalizeOptional(request.getReturnUrl()));
        }
        if (request.getCancelUrl() != null) {
            config.setPaypalCancelUrl(normalizeOptional(request.getCancelUrl()));
        }
        if (request.getWebhookId() != null) {
            config.setPaypalWebhookId(normalizeOptional(request.getWebhookId()));
        }
        if (request.getCurrency() != null) {
            config.setPaypalCurrency(normalizeOptional(request.getCurrency()));
        }
        if (request.getBrandName() != null) {
            config.setPaypalBrandName(normalizeOptional(request.getBrandName()));
        }
        if (request.getLocale() != null) {
            config.setPaypalLocale(normalizeOptional(request.getLocale()));
        }
        if (request.getUserAction() != null) {
            config.setPaypalUserAction(normalizeOptional(request.getUserAction()));
        }
        if (request.getPaymentMethodPreference() != null) {
            config.setPaypalPaymentMethodPreference(normalizeOptional(request.getPaymentMethodPreference()));
        }
        if (request.getShippingPreference() != null) {
            config.setPaypalShippingPreference(normalizeOptional(request.getShippingPreference()));
        }
        if (request.getConversionRateVndToUsd() != null) {
            config.setPaypalConversionRateVndUsd(request.getConversionRateVndToUsd());
        }
        if (request.getConnectTimeoutMs() != null) {
            config.setConnectTimeoutMs(request.getConnectTimeoutMs());
        }
        if (request.getReadTimeoutMs() != null) {
            config.setReadTimeoutMs(request.getReadTimeoutMs());
        }

        paymentConfigRepository.save(config);
        return toPaypalResponse(config, paymentProviderConfigResolver.resolvePaypal(config.isEnabled()));
    }

    private PaymentConfig getOrCreate(String provider) {
        return paymentConfigRepository.findByProviderIgnoreCase(provider)
                .orElseGet(() -> {
                    PaymentConfig config = new PaymentConfig();
                    config.setProvider(provider);
                    config.setEnabled(false);
                    return config;
                });
    }

    private MomoPaymentConfigResponse toMomoResponse(PaymentConfig config, MomoResolvedPaymentConfig resolved) {
        return MomoPaymentConfigResponse.builder()
                .provider(PaymentProviderConfigResolver.MOMO)
                .managedInDatabase(config != null)
                .enabled(resolved.enabled())
                .environment(resolved.environment())
                .hasPartnerCode(hasValue(config != null ? config.getMomoPartnerCodeEnc() : null)
                        || !isBlank(paymentProperties.getMomo().getPartnerCode()))
                .hasAccessKey(hasValue(config != null ? config.getMomoAccessKeyEnc() : null)
                        || !isBlank(paymentProperties.getMomo().getAccessKey()))
                .hasSecretKey(hasValue(config != null ? config.getMomoSecretKeyEnc() : null)
                        || !isBlank(paymentProperties.getMomo().getSecretKey()))
                .createUrl(resolved.createUrl())
                .redirectUrl(resolved.redirectUrl())
                .ipnUrl(resolved.ipnUrl())
                .requestType(resolved.requestType())
                .lang(resolved.lang())
                .connectTimeoutMs(resolved.connectTimeoutMs())
                .readTimeoutMs(resolved.readTimeoutMs())
                .build();
    }

    private PaypalPaymentConfigResponse toPaypalResponse(PaymentConfig config, PaypalResolvedPaymentConfig resolved) {
        return PaypalPaymentConfigResponse.builder()
                .provider(PaymentProviderConfigResolver.PAYPAL)
                .managedInDatabase(config != null)
                .enabled(resolved.enabled())
                .environment(resolved.environment())
                .hasClientId(hasValue(config != null ? config.getPaypalClientIdEnc() : null)
                        || !isBlank(paymentProperties.getPaypal().getClientId()))
                .hasClientSecret(hasValue(config != null ? config.getPaypalClientSecretEnc() : null)
                        || !isBlank(paymentProperties.getPaypal().getClientSecret()))
                .baseUrl(resolved.baseUrl())
                .returnUrl(resolved.returnUrl())
                .cancelUrl(resolved.cancelUrl())
                .webhookId(resolved.webhookId())
                .currency(resolved.currency())
                .brandName(resolved.brandName())
                .locale(resolved.locale())
                .userAction(resolved.userAction())
                .paymentMethodPreference(resolved.paymentMethodPreference())
                .shippingPreference(resolved.shippingPreference())
                .conversionRateVndToUsd(resolved.conversionRateVndToUsd())
                .conversionRateSource(config != null && config.getPaypalConversionRateVndUsd() != null
                        ? "ADMIN_CONFIG" : "ENVIRONMENT_FALLBACK")
                .connectTimeoutMs(resolved.connectTimeoutMs())
                .readTimeoutMs(resolved.readTimeoutMs())
                .build();
    }

    private String encryptOrClear(String rawValue) {
        String normalized = normalizeOptional(rawValue);
        return normalized == null ? null : paymentConfigCipher.encrypt(normalized);
    }

    private boolean hasValue(String value) {
        return !isBlank(value);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
