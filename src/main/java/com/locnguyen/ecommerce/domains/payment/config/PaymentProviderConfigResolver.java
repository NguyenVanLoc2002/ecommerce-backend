package com.locnguyen.ecommerce.domains.payment.config;

import com.locnguyen.ecommerce.common.config.payment.PaymentProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.security.PaymentConfigCipher;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentConfig;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class PaymentProviderConfigResolver {

    public static final String MOMO = "MOMO";
    public static final String PAYPAL = "PAYPAL";

    private final PaymentConfigRepository paymentConfigRepository;
    private final PaymentConfigCipher paymentConfigCipher;
    private final PaymentProperties paymentProperties;

    public MomoResolvedPaymentConfig resolveMomo() {
        return resolveMomo(true);
    }

    public MomoResolvedPaymentConfig resolveMomo(boolean requireEnabled) {
        PaymentConfig config = paymentConfigRepository.findByProviderIgnoreCase(MOMO).orElse(null);
        boolean enabled = config != null ? config.isEnabled() : paymentProperties.getMomo().isEnabled();

        if (requireEnabled && !enabled) {
            throw new AppException(ErrorCode.PAYMENT_CONFIG_DISABLED,
                    "MoMo payment configuration is disabled");
        }

        MomoResolvedPaymentConfig resolved = new MomoResolvedPaymentConfig(
                enabled,
                firstNonBlank(config != null ? config.getEnvironment() : null, paymentProperties.getMomo().getEnvironment()),
                firstNonBlank(decrypt(config != null ? config.getMomoPartnerCodeEnc() : null), paymentProperties.getMomo().getPartnerCode()),
                firstNonBlank(decrypt(config != null ? config.getMomoAccessKeyEnc() : null), paymentProperties.getMomo().getAccessKey()),
                firstNonBlank(decrypt(config != null ? config.getMomoSecretKeyEnc() : null), paymentProperties.getMomo().getSecretKey()),
                firstNonBlank(config != null ? config.getMomoCreateUrl() : null, paymentProperties.getMomo().getCreateUrl()),
                firstNonBlank(config != null ? config.getMomoRedirectUrl() : null, paymentProperties.getMomo().getRedirectUrl()),
                firstNonBlank(config != null ? config.getMomoIpnUrl() : null, paymentProperties.getMomo().getIpnUrl()),
                firstNonBlank(config != null ? config.getMomoRequestType() : null, paymentProperties.getMomo().getRequestType()),
                firstNonBlank(config != null ? config.getMomoLang() : null, paymentProperties.getMomo().getLang()),
                positiveOrDefault(config != null ? config.getConnectTimeoutMs() : null, paymentProperties.getMomo().getConnectTimeoutMs()),
                positiveOrDefault(config != null ? config.getReadTimeoutMs() : null, paymentProperties.getMomo().getReadTimeoutMs())
        );

        validateMomo(resolved, requireEnabled);
        return resolved;
    }

    public PaypalResolvedPaymentConfig resolvePaypal() {
        return resolvePaypal(true);
    }

    public PaypalResolvedPaymentConfig resolvePaypal(boolean requireEnabled) {
        PaymentConfig config = paymentConfigRepository.findByProviderIgnoreCase(PAYPAL).orElse(null);
        boolean enabled = config != null ? config.isEnabled() : paymentProperties.getPaypal().isEnabled();

        if (requireEnabled && !enabled) {
            throw new AppException(ErrorCode.PAYMENT_CONFIG_DISABLED,
                    "PayPal payment configuration is disabled");
        }

        PaypalResolvedPaymentConfig resolved = new PaypalResolvedPaymentConfig(
                enabled,
                firstNonBlank(config != null ? config.getEnvironment() : null, paymentProperties.getPaypal().getEnvironment()),
                firstNonBlank(decrypt(config != null ? config.getPaypalClientIdEnc() : null), paymentProperties.getPaypal().getClientId()),
                firstNonBlank(decrypt(config != null ? config.getPaypalClientSecretEnc() : null), paymentProperties.getPaypal().getClientSecret()),
                firstNonBlank(config != null ? config.getPaypalBaseUrl() : null, paymentProperties.getPaypal().getBaseUrl()),
                firstNonBlank(config != null ? config.getPaypalReturnUrl() : null, paymentProperties.getPaypal().getReturnUrl()),
                firstNonBlank(config != null ? config.getPaypalCancelUrl() : null, paymentProperties.getPaypal().getCancelUrl()),
                firstNonBlank(config != null ? config.getPaypalWebhookId() : null, paymentProperties.getPaypal().getWebhookId()),
                firstNonBlank(config != null ? config.getPaypalCurrency() : null, paymentProperties.getPaypal().getCurrency()),
                firstNonBlank(config != null ? config.getPaypalBrandName() : null, paymentProperties.getPaypal().getBrandName()),
                firstNonBlank(config != null ? config.getPaypalLocale() : null, paymentProperties.getPaypal().getLocale()),
                firstNonBlank(config != null ? config.getPaypalUserAction() : null, paymentProperties.getPaypal().getUserAction()),
                firstNonBlank(config != null ? config.getPaypalPaymentMethodPreference() : null,
                        paymentProperties.getPaypal().getPaymentMethodPreference()),
                firstNonBlank(config != null ? config.getPaypalShippingPreference() : null,
                        paymentProperties.getPaypal().getShippingPreference()),
                config != null && config.getPaypalConversionRateVndUsd() != null
                        ? config.getPaypalConversionRateVndUsd()
                        : paymentProperties.getPaypal().getConversionRateVndToUsd(),
                positiveOrDefault(config != null ? config.getConnectTimeoutMs() : null, paymentProperties.getPaypal().getConnectTimeoutMs()),
                positiveOrDefault(config != null ? config.getReadTimeoutMs() : null, paymentProperties.getPaypal().getReadTimeoutMs())
        );

        validatePaypal(resolved, requireEnabled);
        return resolved;
    }

    private void validateMomo(MomoResolvedPaymentConfig config, boolean requireEnabled) {
        if (!requireEnabled || !config.enabled()) {
            return;
        }
        if (isBlank(config.partnerCode())
                || isBlank(config.accessKey())
                || isBlank(config.secretKey())
                || isBlank(config.createUrl())
                || isBlank(config.redirectUrl())
                || isBlank(config.ipnUrl())) {
            throw new AppException(ErrorCode.PAYMENT_CONFIG_MISSING,
                    "MoMo payment configuration is missing required fields");
        }
    }

    private void validatePaypal(PaypalResolvedPaymentConfig config, boolean requireEnabled) {
        if (!requireEnabled || !config.enabled()) {
            return;
        }
        if (isBlank(config.clientId())
                || isBlank(config.clientSecret())
                || isBlank(config.baseUrl())
                || isBlank(config.returnUrl())
                || isBlank(config.cancelUrl())) {
            throw new AppException(ErrorCode.PAYMENT_CONFIG_MISSING,
                    "PayPal payment configuration is missing required fields");
        }
        if (!"USD".equalsIgnoreCase(config.currency())) {
            throw new AppException(ErrorCode.PAYMENT_CURRENCY_UNSUPPORTED,
                    "PayPal production payments support USD only");
        }
        if (config.conversionRateVndToUsd() == null || config.conversionRateVndToUsd().signum() <= 0) {
            throw new AppException(ErrorCode.PAYMENT_CURRENCY_UNSUPPORTED,
                    "PayPal VND to USD conversion rate must be configured");
        }
    }

    private String decrypt(String ciphertext) {
        return isBlank(ciphertext) ? null : paymentConfigCipher.decrypt(ciphertext);
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return !isBlank(primary) ? primary.trim() : (!isBlank(fallback) ? fallback.trim() : null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
