package com.locnguyen.ecommerce.domains.carrier.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.carrier.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CarrierService {

    PagedResponse<CarrierResponse> getCarriers(CarrierFilter filter, Pageable pageable);

    CarrierResponse getCarrierById(UUID id);

    CarrierResponse createCarrier(CreateCarrierRequest request);

    CarrierResponse updateCarrier(UUID id, UpdateCarrierRequest request);

    CarrierResponse updateConfig(UUID id, UpdateCarrierConfigRequest request);

    CarrierResponse toggleCarrier(UUID id, ToggleCarrierRequest request);

    AhamoveIntegrationResponse getAhamoveIntegration(UUID id);

    AhamoveIntegrationResponse updateAhamoveIntegration(UUID id, UpdateAhamoveIntegrationRequest request);

    AhamoveConnectionTestResponse testAhamoveConnection(UUID id, TestAhamoveConnectionRequest request);

    AhamoveWebhookTokenResponse generateAhamoveWebhookToken(UUID id);

    AhamoveWebhookSetupResponse getAhamoveWebhookSetup(UUID id);
}
