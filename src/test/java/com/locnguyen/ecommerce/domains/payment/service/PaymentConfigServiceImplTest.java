package com.locnguyen.ecommerce.domains.payment.service;

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
import com.locnguyen.ecommerce.domains.payment.service.impl.PaymentConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentConfigServiceImplTest {

    @Mock
    private PaymentConfigRepository paymentConfigRepository;
    @Mock
    private PaymentProviderConfigResolver paymentProviderConfigResolver;
    @Mock
    private PaymentConfigCipher paymentConfigCipher;

    private PaymentProperties paymentProperties;
    private PaymentConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties();
        paymentProperties.getMomo().setPartnerCode("env-partner");
        paymentProperties.getMomo().setAccessKey("env-access");
        paymentProperties.getMomo().setSecretKey("env-secret");
        paymentProperties.getPaypal().setClientId("env-client-id");
        paymentProperties.getPaypal().setClientSecret("env-client-secret");

        service = new PaymentConfigServiceImpl(
                paymentConfigRepository,
                paymentProviderConfigResolver,
                paymentConfigCipher,
                paymentProperties
        );
    }

    @Test
    void getMomoConfig_marksSecretsPresent_fromEnvFallback() {
        when(paymentConfigRepository.findByProviderIgnoreCase("MOMO")).thenReturn(Optional.empty());
        when(paymentProviderConfigResolver.resolveMomo(false)).thenReturn(new MomoResolvedPaymentConfig(
                false,
                "TEST",
                "env-partner",
                "env-access",
                "env-secret",
                "https://momo/create",
                "https://momo/return",
                "https://momo/ipn",
                "captureWallet",
                "vi",
                30_000,
                30_000
        ));

        MomoPaymentConfigResponse response = service.getMomoConfig();

        assertThat(response.isManagedInDatabase()).isFalse();
        assertThat(response.isHasPartnerCode()).isTrue();
        assertThat(response.isHasAccessKey()).isTrue();
        assertThat(response.isHasSecretKey()).isTrue();
    }

    @Test
    void updateMomoConfig_encryptsSecrets_andPersistsDatabaseConfig() {
        PaymentConfig existing = new PaymentConfig();
        existing.setProvider("MOMO");
        existing.setEnabled(false);

        when(paymentConfigRepository.findByProviderIgnoreCase("MOMO")).thenReturn(Optional.of(existing));
        when(paymentConfigCipher.encrypt("db-partner")).thenReturn("enc-partner");
        when(paymentConfigCipher.encrypt("db-access")).thenReturn("enc-access");
        when(paymentConfigCipher.encrypt("db-secret")).thenReturn("enc-secret");
        when(paymentConfigRepository.save(any(PaymentConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentProviderConfigResolver.resolveMomo(true)).thenReturn(new MomoResolvedPaymentConfig(
                true,
                "PROD",
                "db-partner",
                "db-access",
                "db-secret",
                "https://db.momo/create",
                "https://db.momo/return",
                "https://db.momo/ipn",
                "captureWallet",
                "vi",
                45_000,
                46_000
        ));

        MomoPaymentConfigRequest request = new MomoPaymentConfigRequest();
        request.setEnabled(true);
        request.setEnvironment(" PROD ");
        request.setPartnerCode(" db-partner ");
        request.setAccessKey(" db-access ");
        request.setSecretKey(" db-secret ");
        request.setCreateUrl(" https://db.momo/create ");
        request.setRedirectUrl(" https://db.momo/return ");
        request.setIpnUrl(" https://db.momo/ipn ");
        request.setRequestType(" captureWallet ");
        request.setLang(" vi ");
        request.setConnectTimeoutMs(45_000);
        request.setReadTimeoutMs(46_000);

        MomoPaymentConfigResponse response = service.updateMomoConfig(request);
        ArgumentCaptor<PaymentConfig> captor = ArgumentCaptor.forClass(PaymentConfig.class);

        verify(paymentConfigRepository).save(captor.capture());
        verify(paymentProviderConfigResolver).resolveMomo(true);

        PaymentConfig saved = captor.getValue();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getEnvironment()).isEqualTo("PROD");
        assertThat(saved.getMomoPartnerCodeEnc()).isEqualTo("enc-partner");
        assertThat(saved.getMomoAccessKeyEnc()).isEqualTo("enc-access");
        assertThat(saved.getMomoSecretKeyEnc()).isEqualTo("enc-secret");
        assertThat(saved.getMomoCreateUrl()).isEqualTo("https://db.momo/create");
        assertThat(saved.getMomoRedirectUrl()).isEqualTo("https://db.momo/return");
        assertThat(saved.getMomoIpnUrl()).isEqualTo("https://db.momo/ipn");
        assertThat(saved.getMomoRequestType()).isEqualTo("captureWallet");
        assertThat(saved.getMomoLang()).isEqualTo("vi");
        assertThat(saved.getConnectTimeoutMs()).isEqualTo(45_000);
        assertThat(saved.getReadTimeoutMs()).isEqualTo(46_000);

        assertThat(response.isManagedInDatabase()).isTrue();
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getEnvironment()).isEqualTo("PROD");
    }

    @Test
    void updatePaypalConfig_createsConfig_andReturnsResolvedView() {
        when(paymentConfigRepository.findByProviderIgnoreCase("PAYPAL")).thenReturn(Optional.empty());
        when(paymentConfigCipher.encrypt("client-id")).thenReturn("enc-client-id");
        when(paymentConfigCipher.encrypt("client-secret")).thenReturn("enc-client-secret");
        when(paymentConfigRepository.save(any(PaymentConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentProviderConfigResolver.resolvePaypal(true)).thenReturn(new PaypalResolvedPaymentConfig(
                true,
                "SANDBOX",
                "client-id",
                "client-secret",
                "https://api-m.sandbox.paypal.com",
                "https://paypal/return",
                "https://paypal/cancel",
                "WH-123",
                "USD",
                "Locen Studio",
                "en-US",
                "PAY_NOW",
                "IMMEDIATE_PAYMENT_REQUIRED",
                "NO_SHIPPING",
                new BigDecimal("25000"),
                35_000,
                36_000
        ));

        PaypalPaymentConfigRequest request = new PaypalPaymentConfigRequest();
        request.setEnabled(true);
        request.setEnvironment(" SANDBOX ");
        request.setClientId(" client-id ");
        request.setClientSecret(" client-secret ");
        request.setBaseUrl(" https://api-m.sandbox.paypal.com ");
        request.setReturnUrl(" https://paypal/return ");
        request.setCancelUrl(" https://paypal/cancel ");
        request.setWebhookId(" WH-123 ");
        request.setCurrency(" USD ");
        request.setBrandName(" Locen Studio ");
        request.setLocale(" en-US ");
        request.setUserAction(" PAY_NOW ");
        request.setPaymentMethodPreference(" IMMEDIATE_PAYMENT_REQUIRED ");
        request.setShippingPreference(" NO_SHIPPING ");
        request.setConversionRateVndToUsd(new BigDecimal("25000"));
        request.setConnectTimeoutMs(35_000);
        request.setReadTimeoutMs(36_000);

        PaypalPaymentConfigResponse response = service.updatePaypalConfig(request);
        ArgumentCaptor<PaymentConfig> captor = ArgumentCaptor.forClass(PaymentConfig.class);

        verify(paymentConfigRepository).save(captor.capture());
        verify(paymentProviderConfigResolver).resolvePaypal(true);

        PaymentConfig saved = captor.getValue();
        assertThat(saved.getProvider()).isEqualTo(PaymentProviderConfigResolver.PAYPAL);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPaypalClientIdEnc()).isEqualTo("enc-client-id");
        assertThat(saved.getPaypalClientSecretEnc()).isEqualTo("enc-client-secret");
        assertThat(saved.getPaypalBaseUrl()).isEqualTo("https://api-m.sandbox.paypal.com");
        assertThat(saved.getPaypalReturnUrl()).isEqualTo("https://paypal/return");
        assertThat(saved.getPaypalCancelUrl()).isEqualTo("https://paypal/cancel");
        assertThat(saved.getPaypalWebhookId()).isEqualTo("WH-123");
        assertThat(saved.getPaypalCurrency()).isEqualTo("USD");
        assertThat(saved.getPaypalBrandName()).isEqualTo("Locen Studio");
        assertThat(saved.getPaypalLocale()).isEqualTo("en-US");
        assertThat(saved.getPaypalUserAction()).isEqualTo("PAY_NOW");
        assertThat(saved.getPaypalPaymentMethodPreference()).isEqualTo("IMMEDIATE_PAYMENT_REQUIRED");
        assertThat(saved.getPaypalShippingPreference()).isEqualTo("NO_SHIPPING");
        assertThat(saved.getPaypalConversionRateVndUsd()).isEqualByComparingTo(new BigDecimal("25000"));
        assertThat(saved.getConnectTimeoutMs()).isEqualTo(35_000);
        assertThat(saved.getReadTimeoutMs()).isEqualTo(36_000);

        assertThat(response.isManagedInDatabase()).isTrue();
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getBaseUrl()).isEqualTo("https://api-m.sandbox.paypal.com");
    }
}
