package com.locnguyen.ecommerce.domains.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.exception.GlobalExceptionHandler;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.*;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierConnectionStatus;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import com.locnguyen.ecommerce.domains.carrier.service.CarrierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCarrierControllerTest {

    @Mock CarrierService carrierService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AdminCarrierController controller = new AdminCarrierController(carrierService);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createCarrier_returnsCreated() throws Exception {
        CreateCarrierRequest request = new CreateCarrierRequest();
        request.setCode("GHN");
        request.setName("GHN");
        request.setProviderType(CarrierProviderType.GHN);

        CarrierResponse response = CarrierResponse.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .code("GHN")
                .name("GHN")
                .providerType(CarrierProviderType.GHN)
                .status(CarrierStatus.ACTIVE)
                .build();

        when(carrierService.createCarrier(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/carriers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("GHN"))
                .andExpect(jsonPath("$.data.providerType").value("GHN"));
    }

    @Test
    void updateConfig_doesNotExposeRawSecretValues() throws Exception {
        UpdateCarrierConfigRequest request = new UpdateCarrierConfigRequest();
        request.setApiKey("raw-api-key");
        request.setSecretKey("raw-secret");
        request.setWebhookSecret("raw-webhook");
        request.setEnabled(true);

        UUID carrierId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        CarrierResponse response = CarrierResponse.builder()
                .id(carrierId)
                .configEnabled(true)
                .hasApiKey(true)
                .hasSecretKey(true)
                .hasWebhookSecret(true)
                .build();

        when(carrierService.updateConfig(eq(carrierId), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/carriers/{id}/config", carrierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasApiKey").value(true))
                .andExpect(jsonPath("$.data.hasSecretKey").value(true))
                .andExpect(jsonPath("$.data.hasWebhookSecret").value(true))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.secretKey").doesNotExist())
                .andExpect(jsonPath("$.data.webhookSecret").doesNotExist());
    }

    @Test
    void toggleCarrier_returnsUpdatedStatus() throws Exception {
        ToggleCarrierRequest request = new ToggleCarrierRequest();
        request.setActive(false);
        UUID carrierId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        when(carrierService.toggleCarrier(eq(carrierId), any())).thenReturn(CarrierResponse.builder()
                .id(carrierId)
                .status(CarrierStatus.INACTIVE)
                .build());

        mockMvc.perform(patch("/api/v1/admin/carriers/{id}/toggle", carrierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void getCarriers_returnsPagedResponse() throws Exception {
        CarrierResponse item = CarrierResponse.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000004"))
                .code("MOCK")
                .name("Mock Carrier")
                .providerType(CarrierProviderType.MOCK)
                .status(CarrierStatus.ACTIVE)
                .build();
        PagedResponse<CarrierResponse> page = PagedResponse.of(
                new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        when(carrierService.getCarriers(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/carriers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].code").value("MOCK"))
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    void getCarrierById_missingCarrier_returns404() throws Exception {
        UUID carrierId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        when(carrierService.getCarrierById(carrierId))
                .thenThrow(new AppException(ErrorCode.CARRIER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/admin/carriers/{id}", carrierId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARRIER_NOT_FOUND"));
    }

    @Test
    void getAhamoveIntegration_returnsStructuredResponse() throws Exception {
        UUID carrierId = UUID.fromString("00000000-0000-0000-0000-000000000006");
        AhamoveIntegrationResponse response = AhamoveIntegrationResponse.builder()
                .carrierId(carrierId)
                .carrierCode("AHAMOVE")
                .carrierName("AhaMove")
                .enabled(true)
                .hasApiKey(true)
                .hasWebhookSecret(true)
                .phone("84338710667")
                .pickupAddress("123 Nguyen Hue")
                .connectionStatus(CarrierConnectionStatus.CONNECTED)
                .lastHealthCheckAt(LocalDateTime.of(2026, 5, 17, 10, 0))
                .build();

        when(carrierService.getAhamoveIntegration(carrierId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/carriers/{id}/integration/ahamove", carrierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carrierCode").value("AHAMOVE"))
                .andExpect(jsonPath("$.data.phone").value("84338710667"))
                .andExpect(jsonPath("$.data.connectionStatus").value("CONNECTED"));
    }

    @Test
    void testAhamoveConnection_returnsStatusSummary() throws Exception {
        UUID carrierId = UUID.fromString("00000000-0000-0000-0000-000000000007");
        TestAhamoveConnectionRequest request = new TestAhamoveConnectionRequest();
        request.setBaseUrl("https://partner-apistg.ahamove.com");
        request.setPhone("84338710667");

        AhamoveConnectionTestResponse response = AhamoveConnectionTestResponse.builder()
                .success(true)
                .status(CarrierConnectionStatus.CONNECTED)
                .message("AhaMove connection test succeeded")
                .resolvedBaseUrl("https://partner-apistg.ahamove.com")
                .resolvedPhone("84338710667")
                .build();

        when(carrierService.testAhamoveConnection(eq(carrierId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/carriers/{id}/integration/ahamove/test-connection", carrierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.resolvedPhone").value("84338710667"));
    }

    @Test
    void getAhamoveWebhookSetup_returnsWebhookInstructions() throws Exception {
        UUID carrierId = UUID.fromString("00000000-0000-0000-0000-000000000008");
        AhamoveWebhookSetupResponse response = AhamoveWebhookSetupResponse.builder()
                .webhookUrl("https://api.locen.test/api/v1/shipments/webhook/ahamove")
                .authHeader("X-Webhook-Token")
                .hasWebhookToken(true)
                .maskedWebhookToken("********token")
                .instructions(List.of("Paste URL", "Paste token"))
                .build();

        when(carrierService.getAhamoveWebhookSetup(carrierId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/carriers/{id}/integration/ahamove/webhook-setup", carrierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.webhookUrl")
                        .value("https://api.locen.test/api/v1/shipments/webhook/ahamove"))
                .andExpect(jsonPath("$.data.authHeader").value("X-Webhook-Token"))
                .andExpect(jsonPath("$.data.hasWebhookToken").value(true));
    }
}
