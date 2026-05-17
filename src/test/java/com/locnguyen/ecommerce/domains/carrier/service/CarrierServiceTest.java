package com.locnguyen.ecommerce.domains.carrier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.locnguyen.ecommerce.common.config.CarrierProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.security.AesGcmTextCipher;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.carrier.dto.AhamoveConnectionTestResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.AhamoveIntegrationResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.AhamoveWebhookSetupResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.AhamoveWebhookTokenResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.CarrierResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.TestAhamoveConnectionRequest;
import com.locnguyen.ecommerce.domains.carrier.dto.ToggleCarrierRequest;
import com.locnguyen.ecommerce.domains.carrier.dto.UpdateAhamoveIntegrationRequest;
import com.locnguyen.ecommerce.domains.carrier.dto.UpdateCarrierConfigRequest;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierConnectionStatus;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import com.locnguyen.ecommerce.domains.carrier.mapper.CarrierMapper;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierRepository;
import com.locnguyen.ecommerce.domains.carrier.service.impl.CarrierServiceImpl;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveClient;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveConfigResolver;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveResolvedConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CarrierServiceTest {

    @Mock CarrierRepository carrierRepository;
    @Mock CarrierConfigRepository carrierConfigRepository;
    @Mock CarrierMapper carrierMapper;
    @Mock AuditLogService auditLogService;
    @Mock AesGcmTextCipher textCipher;
    @Mock ObjectMapper objectMapper;
    @Mock CarrierProperties carrierProperties;
    @Mock AhamoveClient ahamoveClient;
    @Mock AhamoveConfigResolver ahamoveConfigResolver;

    @InjectMocks CarrierServiceImpl carrierService;

    private static UUID uuid(long n) {
        return new UUID(0L, n);
    }

    private Carrier carrier(UUID id, CarrierStatus status) {
        return carrier(id, status, CarrierProviderType.GHN);
    }

    private Carrier carrier(UUID id, CarrierStatus status, CarrierProviderType providerType) {
        Carrier carrier = new Carrier();
        ReflectionTestUtils.setField(carrier, "id", id);
        carrier.setCode(providerType.name());
        carrier.setName(providerType.name());
        carrier.setProviderType(providerType);
        carrier.setStatus(status);
        return carrier;
    }

    @Nested
    class ToggleCarrier {

        @Test
        void toggleCarrier_true_setsActive() {
            Carrier carrier = carrier(uuid(1), CarrierStatus.INACTIVE);
            ToggleCarrierRequest request = new ToggleCarrierRequest();
            request.setActive(true);

            when(carrierRepository.findById(uuid(1))).thenReturn(Optional.of(carrier));
            when(carrierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(carrierMapper.toResponse(any(Carrier.class))).thenReturn(CarrierResponse.builder()
                    .id(uuid(1))
                    .status(CarrierStatus.ACTIVE)
                    .build());

            CarrierResponse response = carrierService.toggleCarrier(uuid(1), request);

            ArgumentCaptor<Carrier> captor = ArgumentCaptor.forClass(Carrier.class);
            verify(carrierRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(CarrierStatus.ACTIVE);
            assertThat(response.getStatus()).isEqualTo(CarrierStatus.ACTIVE);
        }

        @Test
        void toggleCarrier_false_setsInactive() {
            Carrier carrier = carrier(uuid(2), CarrierStatus.ACTIVE);
            ToggleCarrierRequest request = new ToggleCarrierRequest();
            request.setActive(false);

            when(carrierRepository.findById(uuid(2))).thenReturn(Optional.of(carrier));
            when(carrierRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(carrierMapper.toResponse(any(Carrier.class))).thenReturn(CarrierResponse.builder()
                    .id(uuid(2))
                    .status(CarrierStatus.INACTIVE)
                    .build());

            CarrierResponse response = carrierService.toggleCarrier(uuid(2), request);

            ArgumentCaptor<Carrier> captor = ArgumentCaptor.forClass(Carrier.class);
            verify(carrierRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(CarrierStatus.INACTIVE);
            assertThat(response.getStatus()).isEqualTo(CarrierStatus.INACTIVE);
        }

        @Test
        void toggleCarrier_missingCarrier_throwsCarrierNotFound() {
            ToggleCarrierRequest request = new ToggleCarrierRequest();
            request.setActive(true);
            when(carrierRepository.findById(uuid(99))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> carrierService.toggleCarrier(uuid(99), request))
                    .isInstanceOf(AppException.class)
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CARRIER_NOT_FOUND);
        }
    }

    @Nested
    class UpdateConfig {

        @Test
        void updateConfig_encryptsSecretsBeforeSave() throws Exception {
            Carrier carrier = carrier(uuid(3), CarrierStatus.ACTIVE);
            CarrierConfig config = new CarrierConfig();
            config.setCarrier(carrier);

            UpdateCarrierConfigRequest request = new UpdateCarrierConfigRequest();
            request.setApiKey("api-key");
            request.setSecretKey("secret-key");
            request.setWebhookSecret("webhook-secret");
            request.setBaseUrl("https://carrier.test");
            request.setEnabled(true);
            request.setConfigJson("{\"mode\":\"sandbox\"}");

            when(carrierRepository.findById(uuid(3))).thenReturn(Optional.of(carrier));
            when(carrierConfigRepository.findByCarrierId(uuid(3))).thenReturn(Optional.of(config));
            when(textCipher.encrypt("api-key")).thenReturn("enc-api");
            when(textCipher.encrypt("secret-key")).thenReturn("enc-secret");
            when(textCipher.encrypt("webhook-secret")).thenReturn("enc-webhook");
            when(objectMapper.readTree("{\"mode\":\"sandbox\"}")).thenReturn(new ObjectMapper().readTree("{\"mode\":\"sandbox\"}"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"mode\":\"sandbox\"}");
            when(carrierConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(carrierMapper.toResponse(any(Carrier.class), any(CarrierConfig.class)))
                    .thenReturn(CarrierResponse.builder()
                            .id(uuid(3))
                            .configEnabled(true)
                            .hasApiKey(true)
                            .hasSecretKey(true)
                            .hasWebhookSecret(true)
                            .baseUrl("https://carrier.test")
                            .build());

            CarrierResponse response = carrierService.updateConfig(uuid(3), request);

            ArgumentCaptor<CarrierConfig> captor = ArgumentCaptor.forClass(CarrierConfig.class);
            verify(carrierConfigRepository).save(captor.capture());
            CarrierConfig saved = captor.getValue();
            assertThat(saved.getApiKeyEnc()).isEqualTo("enc-api");
            assertThat(saved.getSecretKeyEnc()).isEqualTo("enc-secret");
            assertThat(saved.getWebhookSecretEnc()).isEqualTo("enc-webhook");
            assertThat(saved.getBaseUrl()).isEqualTo("https://carrier.test");
            assertThat(saved.getConfigJson()).isEqualTo("{\"mode\":\"sandbox\"}");
            assertThat(response.getHasApiKey()).isTrue();
            assertThat(response.getHasSecretKey()).isTrue();
            assertThat(response.getHasWebhookSecret()).isTrue();
        }
    }

    @Nested
    class AhamoveIntegration {

        @Test
        void getAhamoveIntegration_returnsTypedFieldsAndMaskedWebhook() {
            UUID carrierId = uuid(10);
            Carrier carrier = carrier(carrierId, CarrierStatus.ACTIVE, CarrierProviderType.AHAMOVE);
            CarrierConfig config = new CarrierConfig();
            config.setCarrier(carrier);
            config.setEnabled(true);
            config.setBaseUrl("https://partner-apistg.ahamove.com");
            config.setApiKeyEnc("enc-api");
            config.setWebhookSecretEnc("enc-webhook");
            config.setProviderAccountPhone("84338710667");
            config.setProviderBrandName("Locen Studio");
            config.setPickupAddress("123 Nguyen Hue");
            config.setConnectionStatus(CarrierConnectionStatus.CONNECTED);

            when(carrierRepository.findById(carrierId)).thenReturn(Optional.of(carrier));
            when(carrierConfigRepository.findByCarrierId(carrierId)).thenReturn(Optional.of(config));
            when(textCipher.decrypt("enc-webhook")).thenReturn("webhook-secret-1234");

            AhamoveIntegrationResponse response = carrierService.getAhamoveIntegration(carrierId);

            assertThat(response.getCarrierCode()).isEqualTo("AHAMOVE");
            assertThat(response.getPhone()).isEqualTo("84338710667");
            assertThat(response.getPickupAddress()).isEqualTo("123 Nguyen Hue");
            assertThat(response.getConnectionStatus()).isEqualTo(CarrierConnectionStatus.CONNECTED);
            assertThat(response.getHasApiKey()).isTrue();
            assertThat(response.getHasWebhookSecret()).isTrue();
            assertThat(response.getMaskedWebhookToken()).endsWith("1234");
            assertThat(response.getMaskedWebhookToken()).doesNotContain("webhook-secret");
        }

        @Test
        void updateAhamoveIntegration_persistsTypedFieldsAndLegacyJson() throws Exception {
            UUID carrierId = uuid(11);
            Carrier carrier = carrier(carrierId, CarrierStatus.ACTIVE, CarrierProviderType.AHAMOVE);
            CarrierConfig config = new CarrierConfig();
            config.setCarrier(carrier);

            UpdateAhamoveIntegrationRequest request = new UpdateAhamoveIntegrationRequest();
            request.setApiKey("api-key");
            request.setWebhookSecret("webhook-token");
            request.setBaseUrl("https://partner-apistg.ahamove.com");
            request.setEnabled(true);
            request.setPhone("84338710667");
            request.setBrandName("Locen Studio");
            request.setPickupAddress("123 Nguyen Hue");
            request.setPickupShortAddress("Quan 1");
            request.setPickupName("Locen Studio");
            request.setPickupPhone("84338710667");
            request.setDefaultServiceCode("BIKE");
            request.setDefaultPaymentMethod("CASH");

            ObjectNode node = new ObjectMapper().createObjectNode();
            node.put("phone", "84338710667");
            node.put("brandName", "Locen Studio");
            node.put("pickupAddress", "123 Nguyen Hue");
            node.put("pickupShortAddress", "Quan 1");
            node.put("pickupName", "Locen Studio");
            node.put("pickupPhone", "84338710667");
            node.put("groupServiceId", "BIKE");
            node.put("paymentMethod", "CASH");

            when(carrierRepository.findById(carrierId)).thenReturn(Optional.of(carrier));
            when(carrierConfigRepository.findByCarrierId(carrierId)).thenReturn(Optional.of(config));
            when(textCipher.encrypt("api-key")).thenReturn("enc-api");
            when(textCipher.encrypt("webhook-token")).thenReturn("enc-webhook");
            when(objectMapper.createObjectNode()).thenAnswer(inv -> new ObjectMapper().createObjectNode());
            when(objectMapper.readTree(anyString())).thenReturn(node);
            when(objectMapper.writeValueAsString(any())).thenReturn(node.toString());
            when(carrierConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(textCipher.decrypt("enc-webhook")).thenReturn("webhook-token");
            when(ahamoveConfigResolver.resolveAllowDisabled(any())).thenReturn(new AhamoveResolvedConfig(
                    "https://partner-apistg.ahamove.com",
                    "api-key",
                    "84338710667",
                    "Locen Studio",
                    "webhook-token",
                    "123 Nguyen Hue",
                    "Quan 1",
                    "Locen Studio",
                    "84338710667",
                    null,
                    null,
                    "BIKE",
                    "CASH",
                    java.util.List.of()
            ));

            AhamoveIntegrationResponse response = carrierService.updateAhamoveIntegration(carrierId, request);

            ArgumentCaptor<CarrierConfig> captor = ArgumentCaptor.forClass(CarrierConfig.class);
            verify(carrierConfigRepository).save(captor.capture());
            CarrierConfig saved = captor.getValue();
            assertThat(saved.getProviderAccountPhone()).isEqualTo("84338710667");
            assertThat(saved.getProviderBrandName()).isEqualTo("Locen Studio");
            assertThat(saved.getPickupAddress()).isEqualTo("123 Nguyen Hue");
            assertThat(saved.getDefaultServiceCode()).isEqualTo("BIKE");
            assertThat(saved.getConfigJson()).contains("\"pickupAddress\":\"123 Nguyen Hue\"");
            assertThat(response.getDefaultPaymentMethod()).isEqualTo("CASH");
        }

        @Test
        void testAhamoveConnection_success_updatesHealthStatus() {
            UUID carrierId = uuid(12);
            Carrier carrier = carrier(carrierId, CarrierStatus.ACTIVE, CarrierProviderType.AHAMOVE);
            CarrierConfig config = new CarrierConfig();
            config.setCarrier(carrier);
            config.setEnabled(true);
            config.setBaseUrl("https://partner-apistg.ahamove.com");
            config.setProviderAccountPhone("84338710667");

            TestAhamoveConnectionRequest request = new TestAhamoveConnectionRequest();

            AhamoveResolvedConfig resolvedConfig = new AhamoveResolvedConfig(
                    "https://partner-apistg.ahamove.com",
                    "api-key",
                    "84338710667",
                    "Locen Studio",
                    null,
                    "123 Nguyen Hue",
                    null,
                    "Locen Studio",
                    "84338710667",
                    null,
                    null,
                    "BIKE",
                    "CASH",
                    java.util.List.of()
            );

            when(carrierRepository.findById(carrierId)).thenReturn(Optional.of(carrier));
            when(carrierConfigRepository.findByCarrierId(carrierId)).thenReturn(Optional.of(config));
            when(ahamoveConfigResolver.resolveAllowDisabled(any())).thenReturn(resolvedConfig);
            when(carrierConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AhamoveConnectionTestResponse response = carrierService.testAhamoveConnection(carrierId, request);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getStatus()).isEqualTo(CarrierConnectionStatus.CONNECTED);
            verify(ahamoveClient).verifyConnection(resolvedConfig);
            verify(carrierConfigRepository).save(argThat(saved ->
                    saved.getConnectionStatus() == CarrierConnectionStatus.CONNECTED
                            && saved.getLastHealthCheckAt() != null
                            && saved.getLastHealthCheckError() == null));
        }

        @Test
        void generateAhamoveWebhookToken_encryptsAndReturnsRawToken() throws Exception {
            UUID carrierId = uuid(13);
            Carrier carrier = carrier(carrierId, CarrierStatus.ACTIVE, CarrierProviderType.AHAMOVE);
            CarrierConfig config = new CarrierConfig();
            config.setCarrier(carrier);

            when(carrierRepository.findById(carrierId)).thenReturn(Optional.of(carrier));
            when(carrierConfigRepository.findByCarrierId(carrierId)).thenReturn(Optional.of(config));
            when(textCipher.encrypt(any())).thenReturn("enc-webhook");
            when(objectMapper.createObjectNode()).thenAnswer(inv -> new ObjectMapper().createObjectNode());
            when(carrierConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            AhamoveWebhookTokenResponse response = carrierService.generateAhamoveWebhookToken(carrierId);

            assertThat(response.getToken()).isNotBlank();
            assertThat(response.getMaskedToken()).endsWith(response.getToken().substring(response.getToken().length() - 4));
            verify(textCipher).encrypt(response.getToken());
        }

        @Test
        void getAhamoveWebhookSetup_returnsPublicWebhookInstructions() {
            UUID carrierId = uuid(14);
            Carrier carrier = carrier(carrierId, CarrierStatus.ACTIVE, CarrierProviderType.AHAMOVE);
            CarrierConfig config = new CarrierConfig();
            config.setCarrier(carrier);
            config.setWebhookSecretEnc("enc-webhook");

            when(carrierRepository.findById(carrierId)).thenReturn(Optional.of(carrier));
            when(carrierConfigRepository.findByCarrierId(carrierId)).thenReturn(Optional.of(config));
            when(carrierProperties.getWebhookPublicBaseUrl()).thenReturn("https://api.locen.test/");
            when(textCipher.decrypt("enc-webhook")).thenReturn("locen-webhook-token");

            AhamoveWebhookSetupResponse response = carrierService.getAhamoveWebhookSetup(carrierId);

            assertThat(response.getWebhookUrl()).isEqualTo("https://api.locen.test/api/v1/shipments/webhook/ahamove");
            assertThat(response.getAuthHeader()).isEqualTo("X-Webhook-Token");
            assertThat(response.isHasWebhookToken()).isTrue();
            assertThat(response.getInstructions()).isNotEmpty();
        }
    }
}
