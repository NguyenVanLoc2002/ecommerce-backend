package com.locnguyen.ecommerce.domains.shipment.service.impl;

import com.locnguyen.ecommerce.domains.shipment.entity.CarrierWebhookLog;
import com.locnguyen.ecommerce.domains.shipment.repository.CarrierWebhookLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CarrierWebhookLogPersister {

    private final CarrierWebhookLogRepository carrierWebhookLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CarrierWebhookLog createInitialLog(
            String carrierCode,
            String providerOrderId,
            String trackingNumber,
            String eventType,
            String eventKey,
            String payload,
            String headers) {
        CarrierWebhookLog log = new CarrierWebhookLog();
        log.setCarrierCode(carrierCode);
        log.setProviderOrderId(providerOrderId);
        log.setTrackingNumber(trackingNumber);
        log.setEventType(eventType);
        log.setEventKey(eventKey);
        log.setPayload(payload);
        log.setHeaders(headers);
        return carrierWebhookLogRepository.save(log);
    }
}
