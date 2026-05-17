package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierRepository;
import com.locnguyen.ecommerce.domains.shipment.entity.CarrierWebhookLog;
import com.locnguyen.ecommerce.domains.shipment.entity.Shipment;
import com.locnguyen.ecommerce.domains.shipment.enums.ShipmentStatus;
import com.locnguyen.ecommerce.domains.shipment.repository.CarrierWebhookLogRepository;
import com.locnguyen.ecommerce.domains.shipment.repository.ShipmentRepository;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentProviderUpdate;
import com.locnguyen.ecommerce.domains.shipment.service.ShipmentService;
import com.locnguyen.ecommerce.domains.shipment.service.impl.CarrierWebhookLogPersister;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.AhamoveOrderPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AhamoveWebhookService {

    private final CarrierRepository carrierRepository;
    private final CarrierConfigRepository carrierConfigRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;
    private final CarrierWebhookLogRepository carrierWebhookLogRepository;
    private final CarrierWebhookLogPersister carrierWebhookLogPersister;
    private final AhamoveConfigResolver configResolver;
    private final AhamoveCarrierProvider ahamoveCarrierProvider;
    private final ObjectMapper objectMapper;

    @Transactional
    public void receiveWebhook(String rawBody, Map<String, String> rawHeaders) {
        Carrier carrier = carrierRepository.findFirstByProviderType(CarrierProviderType.AHAMOVE)
                .orElseThrow(() -> new AppException(ErrorCode.CARRIER_NOT_FOUND,
                        "No carrier configured for AhaMove"));
        CarrierConfig carrierConfig = carrierConfigRepository.findByCarrierId(carrier.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                        "AhaMove carrier config is missing"));
        AhamoveResolvedConfig resolvedConfig = configResolver.resolve(carrierConfig);

        Map<String, String> headers = normalizeHeaders(rawHeaders);
        if (!verifyToken(headers, resolvedConfig.webhookToken())) {
            throw new AppException(ErrorCode.CARRIER_WEBHOOK_SIGNATURE_INVALID);
        }

        AhamoveOrderPayload payload = parsePayload(rawBody);
        String providerOrderId = trimToNull(payload.getId());
        String trackingNumber = extractTrackingNumber(payload);
        String eventType = trimToNull(payload.getStatus()) != null ? payload.getStatus() : "UNKNOWN";
        String eventKey = buildEventKey(payload, providerOrderId, trackingNumber);

        if (carrierWebhookLogRepository.existsByEventKey(eventKey)) {
            log.info("Skipping duplicate AhaMove webhook: providerOrderId={} eventType={}",
                    providerOrderId, eventType);
            return;
        }

        CarrierWebhookLog webhookLog = carrierWebhookLogPersister.createInitialLog(
                carrier.getCode(), providerOrderId, trackingNumber, eventType, eventKey,
                sanitizePayload(rawBody), sanitizeHeaders(headers));
        webhookLog.setSignatureValid(true);

        try {
            Shipment shipment = resolveShipment(providerOrderId, trackingNumber);
            webhookLog.setShipment(shipment);

            ShipmentStatus mappedStatus = isKnownStatus(payload)
                    ? ahamoveCarrierProvider.mapPayloadStatus(payload)
                    : null;

            ShipmentProviderUpdate update = ShipmentProviderUpdate.builder()
                    .shipmentId(shipment.getId())
                    .status(mappedStatus)
                    .providerStatus(payload.getStatus())
                    .trackingNumber(firstNonBlank(trackingNumber, shipment.getTrackingNumber()))
                    .trackingUrl(firstNonBlank(payload.getSharedLink(), shipment.getProviderTrackingUrl()))
                    .location(mappedStatus != null ? ahamoveCarrierProvider.extractLocation(payload, mappedStatus) : null)
                    .description(mappedStatus != null ? ahamoveCarrierProvider.buildDescription(payload, mappedStatus) : null)
                    .eventTime(mappedStatus != null ? ahamoveCarrierProvider.extractEventTime(payload, mappedStatus) : LocalDateTime.now())
                    .build();
            shipmentService.applyProviderUpdate(update);

            if (!isKnownStatus(payload)) {
                webhookLog.setErrorMessage("Unknown provider status: " + payload.getStatus());
            }
            webhookLog.setProcessed(true);
            webhookLog.setProcessedAt(LocalDateTime.now());
            carrierWebhookLogRepository.save(webhookLog);
        } catch (AppException ex) {
            webhookLog.setErrorMessage(ex.getMessage());
            carrierWebhookLogRepository.save(webhookLog);
            throw ex;
        } catch (Exception ex) {
            webhookLog.setErrorMessage(ex.getMessage());
            carrierWebhookLogRepository.save(webhookLog);
            throw ex;
        }
    }

    private Shipment resolveShipment(String providerOrderId, String trackingNumber) {
        if (providerOrderId != null) {
            return shipmentRepository.findByCarrierShipmentId(providerOrderId)
                    .orElseGet(() -> {
                        if (trackingNumber != null) {
                            return shipmentRepository.findByTrackingNumber(trackingNumber).orElseThrow(() ->
                                    new AppException(ErrorCode.SHIPMENT_PROVIDER_ORDER_NOT_FOUND,
                                            "Shipment not found for AhaMove webhook"));
                        }
                        throw new AppException(ErrorCode.SHIPMENT_PROVIDER_ORDER_NOT_FOUND,
                                "Shipment not found for AhaMove webhook");
                    });
        }
        if (trackingNumber != null) {
            return shipmentRepository.findByTrackingNumber(trackingNumber)
                    .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_PROVIDER_ORDER_NOT_FOUND,
                            "Shipment not found for AhaMove webhook"));
        }
        throw new AppException(ErrorCode.SHIPMENT_PROVIDER_ORDER_NOT_FOUND,
                "AhaMove webhook did not contain provider order id or tracking number");
    }

    private AhamoveOrderPayload parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, AhamoveOrderPayload.class);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Invalid AhaMove webhook payload");
        }
    }

    private boolean verifyToken(Map<String, String> headers, String configuredToken) {
        String token = trimToNull(configuredToken);
        if (token == null) {
            log.warn("AhaMove webhook token is not configured");
            return false;
        }
        String authorization = trimToNull(headers.get("authorization"));
        String apiKey = trimToNull(headers.get("apikey"));
        String webhookToken = trimToNull(headers.get("x-webhook-token"));
        return token.equals(webhookToken)
                || token.equals(apiKey)
                || ("Bearer " + token).equals(authorization);
    }

    private Map<String, String> normalizeHeaders(Map<String, String> rawHeaders) {
        Map<String, String> normalized = new HashMap<>();
        if (rawHeaders == null) {
            return normalized;
        }
        rawHeaders.forEach((key, value) -> {
            if (key != null) {
                normalized.put(key.toLowerCase(Locale.ROOT), value);
            }
        });
        return normalized;
    }

    private String sanitizePayload(String rawBody) {
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            if (node.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("api_key");
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return "{\"sanitized\":false}";
        }
    }

    private String sanitizeHeaders(Map<String, String> headers) {
        try {
            Map<String, String> sanitized = new HashMap<>();
            headers.forEach((key, value) -> {
                if (key.equalsIgnoreCase("authorization")
                        || key.equalsIgnoreCase("apikey")
                        || key.equalsIgnoreCase("x-webhook-token")) {
                    sanitized.put(key, "***");
                } else {
                    sanitized.put(key, value);
                }
            });
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String buildEventKey(AhamoveOrderPayload payload, String providerOrderId, String trackingNumber) {
        String raw = String.join("|",
                firstNonBlank(providerOrderId, ""),
                firstNonBlank(trackingNumber, ""),
                firstNonBlank(payload.getStatus(), ""),
                firstNonBlank(payload.getSubStatus(), ""),
                String.valueOf(payload.getAcceptTime()),
                String.valueOf(payload.getBoardTime()),
                String.valueOf(payload.getPickupTime()),
                String.valueOf(payload.getCompleteTime()),
                String.valueOf(payload.getCancelTime()),
                firstNonBlank(extractPathStatuses(payload), ""));
        return sha256(raw);
    }

    private String extractPathStatuses(AhamoveOrderPayload payload) {
        if (payload.getPath() == null || payload.getPath().isEmpty()) {
            return "";
        }
        return payload.getPath().stream()
                .map(point -> firstNonBlank(point.getStatus(), "")
                        + ":" + point.getCompleteTime()
                        + ":" + point.getFailTime()
                        + ":" + point.getReturnTime())
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
    }

    private boolean isKnownStatus(AhamoveOrderPayload payload) {
        String status = payload.getStatus() != null
                ? payload.getStatus().trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_')
                : "";
        return status.equals("IDLE")
                || status.equals("ASSIGNING")
                || status.equals("ACCEPTED")
                || status.equals("IN_PROCESS")
                || status.equals("COMPLETED")
                || status.equals("CANCELLED")
                || status.equals("FAILED");
    }

    private String extractTrackingNumber(AhamoveOrderPayload payload) {
        if (payload.getPath() == null || payload.getPath().size() < 2) {
            return null;
        }
        return trimToNull(payload.getPath().get(payload.getPath().size() - 1).getTrackingNumber());
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute webhook hash", ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String first, String second) {
        return trimToNull(first) != null ? trimToNull(first) : trimToNull(second);
    }
}
