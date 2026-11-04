package com.locnguyen.ecommerce.domains.payment.config;

import com.locnguyen.ecommerce.common.config.payment.PaymentProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.security.PaymentConfigCipher;
import com.locnguyen.ecommerce.domains.payment.entity.PaymentConfig;
import com.locnguyen.ecommerce.domains.payment.repository.PaymentConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProviderConfigResolverTest {

    @Mock
    private PaymentConfigRepository paymentConfigRepository;
    @Mock
    private PaymentConfigCipher paymentConfigCipher;

    private PaymentProperties paymentProperties;
    private PaymentProviderConfigResolver resolver;

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties();

        paymentProperties.getMomo().setEnabled(true);
        paymentProperties.getMomo().setEnvironment("TEST");
        paymentProperties.getMomo().setPartnerCode("env-partner");
        paymentProperties.getMomo().setAccessKey("env-access");
        paymentProperties.getMomo().setSecretKey("env-secret");
        paymentProperties.getMomo().setCreateUrl("https://env.momo/create");
        paymentProperties.getMomo().setRedirectUrl("https://env.momo/return");
        paymentProperties.getMomo().setIpnUrl("https://env.momo/ipn");
        paymentProperties.getMomo().setRequestType("captureWallet");
        paymentProperties.getMomo().setLang("vi");
        paymentProperties.getMomo().setConnectTimeoutMs(31_000);
        paymentProperties.getMomo().setReadTimeoutMs(32_000);

        paymentProperties.getPaypal().setEnabled(true);
        paymentProperties.getPaypal().setEnvironment("SANDBOX");
        paymentProperties.getPaypal().setClientId("env-client-id");
        paymentProperties.getPaypal().setClientSecret("env-client-secret");
        paymentProperties.getPaypal().setBaseUrl("https://env.paypal");
        paymentProperties.getPaypal().setReturnUrl("https://env.paypal/return");
        paymentProperties.getPaypal().setCancelUrl("https://env.paypal/cancel");
        paymentProperties.getPaypal().setWebhookId("ENV-WH");
        paymentProperties.getPaypal().setCurrency("USD");
        paymentProperties.getPaypal().setBrandName("Env Brand");
        paymentProperties.getPaypal().setLocale("en-US");
        paymentProperties.getPaypal().setUserAction("PAY_NOW");
        paymentProperties.getPaypal().setPaymentMethodPreference("IMMEDIATE_PAYMENT_REQUIRED");
        paymentProperties.getPaypal().setShippingPreference("NO_SHIPPING");
        paymentProperties.getPaypal().setConversionRateVndToUsd(new BigDecimal("25000"));
        paymentProperties.getPaypal().setConnectTimeoutMs(33_000);
        paymentProperties.getPaypal().setReadTimeoutMs(34_000);

        resolver = new PaymentProviderConfigResolver(paymentConfigRepository, paymentConfigCipher, paymentProperties);
    }

    @Test
    void resolveMomo_prefersDatabaseValues_andFallsBackToEnv() {
        PaymentConfig config = new PaymentConfig();
        config.setProvider("MOMO");
        config.setEnabled(true);
        config.setEnvironment("PROD");
        config.setMomoPartnerCodeEnc("enc-partner");
        config.setMomoAccessKeyEnc("enc-access");
        config.setMomoSecretKeyEnc("enc-secret");
        config.setMomoCreateUrl("https://db.momo/create");
        config.setMomoRedirectUrl(null);
        config.setMomoIpnUrl("https://db.momo/ipn");
        config.setMomoRequestType("payWithCC");
        config.setMomoLang("en");
        config.setConnectTimeoutMs(45_000);
        config.setReadTimeoutMs(null);

        when(paymentConfigRepository.findByProviderIgnoreCase("MOMO")).thenReturn(Optional.of(config));
        when(paymentConfigCipher.decrypt("enc-partner")).thenReturn("db-partner");
        when(paymentConfigCipher.decrypt("enc-access")).thenReturn("db-access");
        when(paymentConfigCipher.decrypt("enc-secret")).thenReturn("db-secret");

        MomoResolvedPaymentConfig resolved = resolver.resolveMomo();

        assertThat(resolved.enabled()).isTrue();
        assertThat(resolved.environment()).isEqualTo("PROD");
        assertThat(resolved.partnerCode()).isEqualTo("db-partner");
        assertThat(resolved.accessKey()).isEqualTo("db-access");
        assertThat(resolved.secretKey()).isEqualTo("db-secret");
        assertThat(resolved.createUrl()).isEqualTo("https://db.momo/create");
        assertThat(resolved.redirectUrl()).isEqualTo("https://env.momo/return");
        assertThat(resolved.ipnUrl()).isEqualTo("https://db.momo/ipn");
        assertThat(resolved.requestType()).isEqualTo("payWithCC");
        assertThat(resolved.lang()).isEqualTo("en");
        assertThat(resolved.connectTimeoutMs()).isEqualTo(45_000);
        assertThat(resolved.readTimeoutMs()).isEqualTo(32_000);
    }

    @Test
    void resolveMomo_throwsDisabled_whenRequiredAndDisabled() {
        PaymentConfig config = new PaymentConfig();
        config.setProvider("MOMO");
        config.setEnabled(false);

        when(paymentConfigRepository.findByProviderIgnoreCase("MOMO")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> resolver.resolveMomo())
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_CONFIG_DISABLED);
    }

    @Test
    void resolvePaypal_usesEnvFallback_whenNoDatabaseRowExists() {
        when(paymentConfigRepository.findByProviderIgnoreCase("PAYPAL")).thenReturn(Optional.empty());

        PaypalResolvedPaymentConfig resolved = resolver.resolvePaypal(false);

        assertThat(resolved.enabled()).isTrue();
        assertThat(resolved.clientId()).isEqualTo("env-client-id");
        assertThat(resolved.clientSecret()).isEqualTo("env-client-secret");
        assertThat(resolved.baseUrl()).isEqualTo("https://env.paypal");
        assertThat(resolved.webhookId()).isEqualTo("ENV-WH");
        verify(paymentConfigCipher, never()).decrypt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolvePaypal_throwsMissing_whenEnabledButRequiredFieldsAbsent() {
        PaymentConfig config = new PaymentConfig();
        config.setProvider("PAYPAL");
        config.setEnabled(true);
        config.setPaypalClientIdEnc("enc-client");
        config.setPaypalClientSecretEnc("enc-secret");
        config.setPaypalBaseUrl(" ");
        config.setPaypalReturnUrl(null);
        config.setPaypalCancelUrl("https://db.paypal/cancel");

        when(paymentConfigRepository.findByProviderIgnoreCase("PAYPAL")).thenReturn(Optional.of(config));
        when(paymentConfigCipher.decrypt("enc-client")).thenReturn("db-client-id");
        when(paymentConfigCipher.decrypt("enc-secret")).thenReturn("db-client-secret");
        paymentProperties.getPaypal().setBaseUrl("");
        paymentProperties.getPaypal().setReturnUrl("");

        assertThatThrownBy(() -> resolver.resolvePaypal())
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_CONFIG_MISSING);
    }
}
