package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.security.AesGcmTextCipher;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AhamoveConfigResolverTest {

    @Mock
    private AesGcmTextCipher textCipher;

    private AhamoveProperties properties;
    private AhamoveConfigResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new AhamoveProperties();
        properties.setBaseUrl("https://partner-apistg.ahamove.com");
        properties.setApiKey("env-api-key");
        properties.setPhone("84999999999");
        properties.setBrandName("Env Brand");
        properties.setWebhookToken("env-webhook");
        properties.setGroupServiceId("TRUCK");
        properties.setPaymentMethod("CASH");
        resolver = new AhamoveConfigResolver(textCipher, properties, new ObjectMapper());
    }

    @Test
    void resolveAllowDisabledExplicit_doesNotFallbackToPropertiesForSensitiveFields() {
        CarrierConfig config = new CarrierConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://partner-apistg.ahamove.com");
        config.setPickupAddress("123 Nguyen Hue");
        config.setPickupPhone("84338710667");
        config.setDefaultServiceCode("BIKE");

        assertThatThrownBy(() -> resolver.resolveAllowDisabledExplicit(config))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CARRIER_CONFIG_MISSING);
    }

    @Test
    void resolveAllowDisabled_stillFallsBackToProperties() {
        CarrierConfig config = new CarrierConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://partner-apistg.ahamove.com");
        config.setPickupAddress("123 Nguyen Hue");
        config.setPickupPhone("84338710667");
        config.setDefaultServiceCode("BIKE");

        AhamoveResolvedConfig resolved = resolver.resolveAllowDisabled(config);

        assertThat(resolved.apiKey()).isEqualTo("env-api-key");
        assertThat(resolved.phone()).isEqualTo("84999999999");
        assertThat(resolved.groupServiceId()).isEqualTo("BIKE");
    }

    @Test
    void resolveAllowDisabledExplicit_usesStoredSecretsWhenPresent() {
        CarrierConfig config = new CarrierConfig();
        config.setEnabled(true);
        config.setApiKeyEnc("enc-api");
        config.setBaseUrl("https://partner-apistg.ahamove.com");
        config.setProviderAccountPhone("84338710667");
        config.setPickupAddress("123 Nguyen Hue");
        config.setPickupPhone("84338710667");
        config.setDefaultServiceCode("BIKE");

        when(textCipher.decrypt("enc-api")).thenReturn("db-api-key");

        AhamoveResolvedConfig resolved = resolver.resolveAllowDisabledExplicit(config);

        assertThat(resolved.apiKey()).isEqualTo("db-api-key");
        assertThat(resolved.phone()).isEqualTo("84338710667");
        assertThat(resolved.groupServiceId()).isEqualTo("BIKE");
    }
}
