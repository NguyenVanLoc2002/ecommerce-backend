package com.locnguyen.ecommerce.domains.carrier.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.locnguyen.ecommerce.common.config.CarrierProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.security.AesGcmTextCipher;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.carrier.dto.*;
import com.locnguyen.ecommerce.domains.carrier.entity.Carrier;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierConnectionStatus;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierStatus;
import com.locnguyen.ecommerce.domains.carrier.mapper.CarrierMapper;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierConfigRepository;
import com.locnguyen.ecommerce.domains.carrier.repository.CarrierRepository;
import com.locnguyen.ecommerce.domains.carrier.service.CarrierService;
import com.locnguyen.ecommerce.domains.carrier.specification.CarrierSpecification;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveClient;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveConfigData;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveConfigResolver;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveMapper;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.AhamoveResolvedConfig;
import com.locnguyen.ecommerce.infrastructure.external.ahamove.dto.AhamoveEstimateOptionResponse;
import com.locnguyen.ecommerce.domains.order.entity.Order;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierServiceImpl implements CarrierService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CarrierRepository carrierRepository;
    private final CarrierConfigRepository carrierConfigRepository;
    private final CarrierMapper carrierMapper;
    private final AuditLogService auditLogService;
    private final AesGcmTextCipher textCipher;
    private final ObjectMapper objectMapper;
    private final CarrierProperties carrierProperties;
    private final AhamoveClient ahamoveClient;
    private final AhamoveConfigResolver ahamoveConfigResolver;
    private final AhamoveMapper ahamoveMapper;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CarrierResponse> getCarriers(CarrierFilter filter, Pageable pageable) {
        Page<Carrier> page = carrierRepository.findAll(CarrierSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(carrierMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public CarrierResponse getCarrierById(UUID id) {
        return carrierMapper.toResponse(findCarrier(id));
    }

    @Override
    @Transactional
    public CarrierResponse createCarrier(CreateCarrierRequest request) {
        String code = normalizeRequired(request.getCode());
        if (carrierRepository.existsByCodeIgnoreCase(code)) {
            throw new AppException(ErrorCode.CONFLICT, "Carrier code is already in use");
        }

        Carrier carrier = new Carrier();
        carrier.setCode(code);
        carrier.setName(normalizeRequired(request.getName()));
        carrier.setProviderType(request.getProviderType());
        carrier.setStatus(request.getStatus() != null ? request.getStatus() : CarrierStatus.ACTIVE);
        carrier.setLogoUrl(normalizeOptional(request.getLogoUrl()));
        carrier.setDescription(normalizeOptional(request.getDescription()));
        carrier = carrierRepository.save(carrier);

        log.info("Carrier created: id={} code={} provider={}",
                carrier.getId(), carrier.getCode(), carrier.getProviderType());
        auditLogService.log(AuditAction.CARRIER_CREATED, "CARRIER", carrier.getId().toString(),
                "code=" + carrier.getCode());
        return carrierMapper.toResponse(carrier);
    }

    @Override
    @Transactional
    public CarrierResponse updateCarrier(UUID id, UpdateCarrierRequest request) {
        Carrier carrier = findCarrier(id);

        if (request.getCode() != null) {
            String code = normalizeRequired(request.getCode());
            if (!code.equalsIgnoreCase(carrier.getCode()) && carrierRepository.existsByCodeIgnoreCase(code)) {
                throw new AppException(ErrorCode.CONFLICT, "Carrier code is already in use");
            }
            carrier.setCode(code);
        }
        if (request.getName() != null) {
            carrier.setName(normalizeRequired(request.getName()));
        }
        if (request.getProviderType() != null) {
            carrier.setProviderType(request.getProviderType());
        }
        if (request.getStatus() != null) {
            carrier.setStatus(request.getStatus());
        }
        if (request.getLogoUrl() != null) {
            carrier.setLogoUrl(normalizeOptional(request.getLogoUrl()));
        }
        if (request.getDescription() != null) {
            carrier.setDescription(normalizeOptional(request.getDescription()));
        }

        carrier = carrierRepository.save(carrier);
        auditLogService.log(AuditAction.CARRIER_UPDATED, "CARRIER", carrier.getId().toString());
        return carrierMapper.toResponse(carrier);
    }

    @Override
    @Transactional
    public CarrierResponse updateConfig(UUID id, UpdateCarrierConfigRequest request) {
        Carrier carrier = findCarrier(id);
        CarrierConfig config = getOrCreateConfig(carrier);

        if (request.getApiKey() != null) {
            config.setApiKeyEnc(encryptOrClear(request.getApiKey()));
        }
        if (request.getSecretKey() != null) {
            config.setSecretKeyEnc(encryptOrClear(request.getSecretKey()));
        }
        if (request.getWebhookSecret() != null) {
            config.setWebhookSecretEnc(encryptOrClear(request.getWebhookSecret()));
        }
        if (request.getBaseUrl() != null) {
            config.setBaseUrl(normalizeOptional(request.getBaseUrl()));
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (request.getConfigJson() != null) {
            config.setConfigJson(normalizeJson(request.getConfigJson()));
        }

        if (carrier.getProviderType() == CarrierProviderType.AHAMOVE) {
            syncAhamoveTypedFieldsFromLegacyJson(config);
            ensureConnectionStatusDefault(config);
        }

        carrierConfigRepository.save(config);
        auditLogService.log(AuditAction.CARRIER_CONFIG_UPDATED, "CARRIER", carrier.getId().toString(),
                "enabled=" + config.isEnabled());
        return carrierMapper.toResponse(carrier, config);
    }

    @Override
    @Transactional
    public CarrierResponse toggleCarrier(UUID id, ToggleCarrierRequest request) {
        Carrier carrier = findCarrier(id);
        carrier.setStatus(Boolean.TRUE.equals(request.getActive())
                ? CarrierStatus.ACTIVE
                : CarrierStatus.INACTIVE);
        carrier = carrierRepository.save(carrier);

        auditLogService.log(AuditAction.CARRIER_STATUS_CHANGED, "CARRIER", carrier.getId().toString(),
                "status=" + carrier.getStatus());
        return carrierMapper.toResponse(carrier);
    }

    @Override
    @Transactional(readOnly = true)
    public AhamoveIntegrationResponse getAhamoveIntegration(UUID id) {
        Carrier carrier = requireAhamoveCarrier(id);
        CarrierConfig config = carrierConfigRepository.findByCarrierId(id)
                .orElseGet(() -> newCarrierConfig(carrier));
        syncAhamoveTypedFieldsFromLegacyJson(config);
        ensureConnectionStatusDefault(config);
        return toAhamoveIntegrationResponse(carrier, config);
    }

    @Override
    @Transactional
    public AhamoveIntegrationResponse updateAhamoveIntegration(UUID id, UpdateAhamoveIntegrationRequest request) {
        Carrier carrier = requireAhamoveCarrier(id);
        CarrierConfig config = getOrCreateConfig(carrier);

        if (request.getApiKey() != null) {
            config.setApiKeyEnc(encryptOrClear(request.getApiKey()));
        }
        if (request.getSecretKey() != null) {
            config.setSecretKeyEnc(encryptOrClear(request.getSecretKey()));
        }
        if (request.getWebhookSecret() != null) {
            config.setWebhookSecretEnc(encryptOrClear(request.getWebhookSecret()));
        }
        if (request.getBaseUrl() != null) {
            config.setBaseUrl(normalizeOptional(request.getBaseUrl()));
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }

        applyTypedAhamoveSettings(config, request);
        ensureConnectionStatusDefault(config);
        validateAhamoveIntegration(config);
        config.setConfigJson(buildLegacyAhamoveConfigJson(config));

        carrierConfigRepository.save(config);
        auditLogService.log(AuditAction.CARRIER_CONFIG_UPDATED, "CARRIER", carrier.getId().toString(),
                "provider=AHAMOVE enabled=" + config.isEnabled());
        return toAhamoveIntegrationResponse(carrier, config);
    }

    @Override
    @Transactional
    public AhamoveConnectionTestResponse testAhamoveConnection(UUID id, TestAhamoveConnectionRequest request) {
        Carrier carrier = requireAhamoveCarrier(id);
        CarrierConfig persistedConfig = carrierConfigRepository.findByCarrierId(id).orElse(null);
        CarrierConfig effectiveConfig = copyForTest(carrier, persistedConfig);

        if (effectiveConfig == null) {
            effectiveConfig = newCarrierConfig(carrier);
        }
        effectiveConfig.setEnabled(true);
        applyConnectionOverrides(effectiveConfig, request);
        syncAhamoveTypedFieldsFromLegacyJson(effectiveConfig);

        List<String> missingQuoteFields = collectAhamoveQuoteBlockingFields(effectiveConfig);
        boolean quoteReady = missingQuoteFields.isEmpty();
        String resolvedBaseUrl = firstNonBlank(effectiveConfig.getBaseUrl());
        String resolvedPhone = firstNonBlank(effectiveConfig.getProviderAccountPhone());
        try {
            AhamoveResolvedConfig resolvedConfig = ahamoveConfigResolver.resolveAllowDisabledExplicit(effectiveConfig);
            resolvedBaseUrl = resolvedConfig.apiBaseUrl();
            resolvedPhone = resolvedConfig.phone();
            ahamoveClient.verifyConnection(resolvedConfig);
            if (!quoteReady && !hasQuoteProbe(request)) {
                String message = buildQuoteBlockedMessage(
                        "AhaMove authentication succeeded but live quotes are blocked", missingQuoteFields);
                updateConnectionHealth(persistedConfig, CarrierConnectionStatus.FAILED, message);
                return AhamoveConnectionTestResponse.builder()
                        .success(false)
                        .status(CarrierConnectionStatus.FAILED)
                        .message(message)
                        .resolvedBaseUrl(resolvedBaseUrl)
                        .resolvedPhone(resolvedPhone)
                        .quoteReady(false)
                        .quoteVerified(false)
                        .missingQuoteFields(missingQuoteFields)
                        .build();
            }

            boolean quoteVerified = false;
            if (hasQuoteProbe(request)) {
                if (!quoteReady) {
                    String message = buildQuoteBlockedMessage(
                            "AhaMove quote probe is blocked by missing config fields", missingQuoteFields);
                    updateConnectionHealth(persistedConfig, CarrierConnectionStatus.FAILED, message);
                    return AhamoveConnectionTestResponse.builder()
                            .success(false)
                            .status(CarrierConnectionStatus.FAILED)
                            .message(message)
                            .resolvedBaseUrl(resolvedBaseUrl)
                            .resolvedPhone(resolvedPhone)
                            .quoteReady(false)
                            .quoteVerified(false)
                            .missingQuoteFields(missingQuoteFields)
                            .build();
                }
                List<AhamoveEstimateOptionResponse> probeOptions = ahamoveClient.estimateFee(
                        ahamoveMapper.toEstimateRequest(buildProbeOrder(request), resolvedConfig),
                        resolvedConfig);
                if (probeOptions.isEmpty()) {
                    throw new AppException(ErrorCode.CARRIER_REQUEST_FAILED,
                            "AhaMove quote probe returned no available options");
                }
                quoteVerified = true;
            }
            updateConnectionHealth(persistedConfig, CarrierConnectionStatus.CONNECTED, null);
            return AhamoveConnectionTestResponse.builder()
                    .success(true)
                    .status(CarrierConnectionStatus.CONNECTED)
                    .message(quoteVerified
                            ? "AhaMove connection and quote test succeeded"
                            : "AhaMove authentication succeeded; live quote config is ready")
                    .resolvedBaseUrl(resolvedBaseUrl)
                    .resolvedPhone(resolvedPhone)
                    .quoteReady(quoteReady)
                    .quoteVerified(quoteVerified)
                    .missingQuoteFields(missingQuoteFields.isEmpty() ? null : missingQuoteFields)
                    .build();
        } catch (AppException ex) {
            updateConnectionHealth(persistedConfig, CarrierConnectionStatus.FAILED, sanitize(ex.getMessage()));
            return AhamoveConnectionTestResponse.builder()
                    .success(false)
                    .status(CarrierConnectionStatus.FAILED)
                    .message(sanitize(ex.getMessage()))
                    .resolvedBaseUrl(resolvedBaseUrl)
                    .resolvedPhone(resolvedPhone)
                    .quoteReady(quoteReady)
                    .quoteVerified(false)
                    .missingQuoteFields(missingQuoteFields.isEmpty() ? null : missingQuoteFields)
                    .build();
        }
    }

    @Override
    @Transactional
    public AhamoveWebhookTokenResponse generateAhamoveWebhookToken(UUID id) {
        Carrier carrier = requireAhamoveCarrier(id);
        CarrierConfig config = getOrCreateConfig(carrier);

        String rawToken = generateSecureToken();
        config.setWebhookSecretEnc(textCipher.encrypt(rawToken));
        ensureConnectionStatusDefault(config);
        config.setConfigJson(buildLegacyAhamoveConfigJson(config));
        carrierConfigRepository.save(config);

        auditLogService.log(AuditAction.CARRIER_CONFIG_UPDATED, "CARRIER", carrier.getId().toString(),
                "provider=AHAMOVE webhookTokenRotated=true");
        return AhamoveWebhookTokenResponse.builder()
                .token(rawToken)
                .maskedToken(maskSecret(rawToken))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AhamoveWebhookSetupResponse getAhamoveWebhookSetup(UUID id) {
        Carrier carrier = requireAhamoveCarrier(id);
        CarrierConfig config = carrierConfigRepository.findByCarrierId(id).orElse(null);
        String publicBaseUrl = normalizeOptional(carrierProperties.getWebhookPublicBaseUrl());
        if (publicBaseUrl == null) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                    "Carrier webhook public base URL is not configured");
        }

        String decryptedWebhookToken = config != null ? decrypt(config.getWebhookSecretEnc()) : null;
        return AhamoveWebhookSetupResponse.builder()
                .webhookUrl(removeTrailingSlash(publicBaseUrl) + "/api/v1/shipments/webhook/ahamove")
                .authHeader("X-Webhook-Token")
                .authScheme(null)
                .hasWebhookToken(decryptedWebhookToken != null)
                .maskedWebhookToken(maskSecret(decryptedWebhookToken))
                .instructions(List.of(
                        "Open the AhaMove partner portal webhook settings.",
                        "Paste the webhook URL shown here into Webhook URL.",
                        "Set Auth Header to X-Webhook-Token.",
                        "Paste the generated webhook token into Auth Token.",
                        "Leave Auth URL and Auth Body empty unless AhaMove requires them."
                ))
                .build();
    }

    private Carrier findCarrier(UUID id) {
        return carrierRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CARRIER_NOT_FOUND));
    }

    private Carrier requireAhamoveCarrier(UUID id) {
        Carrier carrier = findCarrier(id);
        if (carrier.getProviderType() != CarrierProviderType.AHAMOVE) {
            throw new AppException(ErrorCode.CARRIER_PROVIDER_NOT_SUPPORTED,
                    "Carrier " + carrier.getCode() + " is not an AhaMove provider");
        }
        return carrier;
    }

    private CarrierConfig getOrCreateConfig(Carrier carrier) {
        return carrierConfigRepository.findByCarrierId(carrier.getId())
                .orElseGet(() -> newCarrierConfig(carrier));
    }

    private CarrierConfig newCarrierConfig(Carrier carrier) {
        CarrierConfig config = new CarrierConfig();
        config.setCarrier(carrier);
        config.setConnectionStatus(CarrierConnectionStatus.NOT_CONFIGURED);
        return config;
    }

    private CarrierConfig copyForTest(Carrier carrier, CarrierConfig source) {
        if (source == null) {
            return newCarrierConfig(carrier);
        }
        CarrierConfig copy = newCarrierConfig(carrier);
        copy.setApiKeyEnc(source.getApiKeyEnc());
        copy.setSecretKeyEnc(source.getSecretKeyEnc());
        copy.setWebhookSecretEnc(source.getWebhookSecretEnc());
        copy.setBaseUrl(source.getBaseUrl());
        copy.setEnabled(source.isEnabled());
        copy.setProviderAccountId(source.getProviderAccountId());
        copy.setProviderAccountPhone(source.getProviderAccountPhone());
        copy.setProviderBrandName(source.getProviderBrandName());
        copy.setPickupAddress(source.getPickupAddress());
        copy.setPickupShortAddress(source.getPickupShortAddress());
        copy.setPickupName(source.getPickupName());
        copy.setPickupPhone(source.getPickupPhone());
        copy.setPickupLat(source.getPickupLat());
        copy.setPickupLng(source.getPickupLng());
        copy.setDefaultServiceCode(source.getDefaultServiceCode());
        copy.setDefaultPaymentMethod(source.getDefaultPaymentMethod());
        copy.setConnectionStatus(source.getConnectionStatus());
        copy.setConfigJson(source.getConfigJson());
        return copy;
    }

    private void applyTypedAhamoveSettings(CarrierConfig config, UpdateAhamoveIntegrationRequest request) {
        config.setProviderAccountPhone(normalizeOptional(request.getPhone()));
        config.setProviderBrandName(normalizeOptional(request.getBrandName()));
        config.setPickupAddress(normalizeOptional(request.getPickupAddress()));
        config.setPickupShortAddress(normalizeOptional(request.getPickupShortAddress()));
        config.setPickupName(normalizeOptional(request.getPickupName()));
        config.setPickupPhone(normalizeOptional(request.getPickupPhone()));
        config.setPickupLat(request.getPickupLat());
        config.setPickupLng(request.getPickupLng());
        config.setDefaultServiceCode(normalizeOptional(request.getDefaultServiceCode()));
        config.setDefaultPaymentMethod(normalizeOptional(request.getDefaultPaymentMethod()));
    }

    private void applyConnectionOverrides(CarrierConfig config, TestAhamoveConnectionRequest request) {
        if (request == null) {
            return;
        }
        if (request.getApiKey() != null) {
            config.setApiKeyEnc(encryptOrClear(request.getApiKey()));
        }
        if (request.getBaseUrl() != null) {
            config.setBaseUrl(normalizeOptional(request.getBaseUrl()));
        }
        if (request.getPhone() != null) {
            config.setProviderAccountPhone(normalizeOptional(request.getPhone()));
        }
    }

    private boolean hasQuoteProbe(TestAhamoveConnectionRequest request) {
        return request != null
                && normalizeOptional(request.getProbeReceiverPhone()) != null
                && normalizeOptional(request.getProbeShippingStreet()) != null
                && normalizeOptional(request.getProbeShippingDistrict()) != null
                && normalizeOptional(request.getProbeShippingCity()) != null;
    }

    private Order buildProbeOrder(TestAhamoveConnectionRequest request) {
        Order order = new Order();
        order.setOrderCode("AHAMOVE-QUOTE-PROBE");
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setReceiverName(firstNonBlank(request.getProbeReceiverName(), "Quote Probe"));
        order.setReceiverPhone(normalizeRequired(request.getProbeReceiverPhone()));
        order.setShippingStreet(normalizeRequired(request.getProbeShippingStreet()));
        order.setShippingWard(normalizeOptional(request.getProbeShippingWard()));
        order.setShippingDistrict(normalizeRequired(request.getProbeShippingDistrict()));
        order.setShippingCity(normalizeRequired(request.getProbeShippingCity()));
        BigDecimal subTotal = request.getProbeSubTotal() != null
                ? request.getProbeSubTotal()
                : new BigDecimal("100000");
        order.setSubTotal(subTotal);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(subTotal);
        return order;
    }

    private void validateAhamoveIntegration(CarrierConfig config) {
        if (!config.isEnabled()) {
            return;
        }
        validateAhamoveQuoteCriticalFields(config);
        ahamoveConfigResolver.resolveAllowDisabledExplicit(config);
    }

    private void validateAhamoveQuoteCriticalFields(CarrierConfig config) {
        List<String> missingFields = collectAhamoveQuoteBlockingFields(config);
        if (!missingFields.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "AhaMove config is missing required fields: " + String.join(", ", missingFields));
        }
    }

    private void ensureConnectionStatusDefault(CarrierConfig config) {
        if (config.getConnectionStatus() == null) {
            config.setConnectionStatus(CarrierConnectionStatus.NOT_CONFIGURED);
        }
    }

    private AhamoveIntegrationResponse toAhamoveIntegrationResponse(Carrier carrier, CarrierConfig config) {
        List<String> missingQuoteFields = collectAhamoveQuoteBlockingFields(config);
        String webhookToken = decrypt(config.getWebhookSecretEnc());
        return AhamoveIntegrationResponse.builder()
                .carrierId(carrier.getId())
                .carrierCode(carrier.getCode())
                .carrierName(carrier.getName())
                .enabled(config.isEnabled())
                .baseUrl(firstNonBlank(config.getBaseUrl()))
                .hasApiKey(hasEncryptedValue(config.getApiKeyEnc()))
                .hasSecretKey(hasEncryptedValue(config.getSecretKeyEnc()))
                .hasWebhookSecret(hasEncryptedValue(config.getWebhookSecretEnc()))
                .phone(firstNonBlank(config.getProviderAccountPhone()))
                .brandName(firstNonBlank(config.getProviderBrandName()))
                .pickupAddress(firstNonBlank(config.getPickupAddress()))
                .pickupShortAddress(firstNonBlank(config.getPickupShortAddress()))
                .pickupName(firstNonBlank(config.getPickupName()))
                .pickupPhone(firstNonBlank(config.getPickupPhone()))
                .pickupLat(config.getPickupLat())
                .pickupLng(config.getPickupLng())
                .defaultServiceCode(firstNonBlank(config.getDefaultServiceCode()))
                .defaultPaymentMethod(firstNonBlank(config.getDefaultPaymentMethod()))
                .quoteReady(missingQuoteFields.isEmpty())
                .missingQuoteFields(missingQuoteFields.isEmpty() ? null : missingQuoteFields)
                .connectionStatus(config.getConnectionStatus())
                .lastHealthCheckAt(config.getLastHealthCheckAt())
                .lastHealthCheckError(config.getLastHealthCheckError())
                .maskedWebhookToken(maskSecret(webhookToken))
                .build();
    }

    private void syncAhamoveTypedFieldsFromLegacyJson(CarrierConfig config) {
        if (config == null || normalizeOptional(config.getConfigJson()) == null) {
            return;
        }
        try {
            AhamoveConfigData data = objectMapper.readValue(config.getConfigJson(), AhamoveConfigData.class);
            if (config.getProviderAccountPhone() == null) {
                config.setProviderAccountPhone(normalizeOptional(data.getPhone()));
            }
            if (config.getProviderBrandName() == null) {
                config.setProviderBrandName(normalizeOptional(data.getBrandName()));
            }
            if (config.getPickupAddress() == null) {
                config.setPickupAddress(normalizeOptional(data.getPickupAddress()));
            }
            if (config.getPickupShortAddress() == null) {
                config.setPickupShortAddress(normalizeOptional(data.getPickupShortAddress()));
            }
            if (config.getPickupName() == null) {
                config.setPickupName(normalizeOptional(data.getPickupName()));
            }
            if (config.getPickupPhone() == null) {
                config.setPickupPhone(normalizeOptional(data.getPickupPhone()));
            }
            if (config.getPickupLat() == null) {
                config.setPickupLat(data.getPickupLat());
            }
            if (config.getPickupLng() == null) {
                config.setPickupLng(data.getPickupLng());
            }
            if (config.getDefaultServiceCode() == null) {
                config.setDefaultServiceCode(normalizeOptional(data.getGroupServiceId()));
            }
            if (config.getDefaultPaymentMethod() == null) {
                config.setDefaultPaymentMethod(normalizeOptional(data.getPaymentMethod()));
            }
        } catch (Exception ex) {
            log.warn("Ignoring invalid legacy AhaMove configJson during typed sync for carrierId={}",
                    config.getCarrier() != null ? config.getCarrier().getId() : null);
        }
    }

    private String buildLegacyAhamoveConfigJson(CarrierConfig config) {
        try {
            ObjectNode node = parseLegacyConfigJson(config.getConfigJson());
            putOrRemove(node, "phone", config.getProviderAccountPhone());
            putOrRemove(node, "brandName", config.getProviderBrandName());
            putOrRemove(node, "pickupAddress", config.getPickupAddress());
            putOrRemove(node, "pickupShortAddress", config.getPickupShortAddress());
            putOrRemove(node, "pickupName", config.getPickupName());
            putOrRemove(node, "pickupPhone", config.getPickupPhone());
            putOrRemove(node, "groupServiceId", config.getDefaultServiceCode());
            putOrRemove(node, "paymentMethod", config.getDefaultPaymentMethod());

            if (config.getPickupLat() != null) {
                node.put("pickupLat", config.getPickupLat());
            } else {
                node.remove("pickupLat");
            }
            if (config.getPickupLng() != null) {
                node.put("pickupLng", config.getPickupLng());
            } else {
                node.remove("pickupLng");
            }

            node.remove("webhookToken");
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Failed to build AhaMove legacy config JSON");
        }
    }

    private ObjectNode parseLegacyConfigJson(String configJson) {
        String normalized = normalizeOptional(configJson);
        if (normalized == null) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(normalized);
            return node.isObject() ? (ObjectNode) node.deepCopy() : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private void putOrRemove(ObjectNode node, String key, String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            node.remove(key);
        } else {
            node.put(key, normalized);
        }
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Field value must not be blank");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String encryptOrClear(String rawValue) {
        String normalized = normalizeOptional(rawValue);
        return normalized == null ? null : textCipher.encrypt(normalized);
    }

    private String normalizeJson(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(normalized));
        } catch (Exception ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "configJson must be valid JSON");
        }
    }

    private boolean hasEncryptedValue(String encryptedValue) {
        return normalizeOptional(encryptedValue) != null;
    }

    private String decrypt(String encryptedValue) {
        String normalized = normalizeOptional(encryptedValue);
        if (normalized == null) {
            return null;
        }
        return textCipher.decrypt(normalized);
    }

    private String maskSecret(String rawValue) {
        String normalized = normalizeOptional(rawValue);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= 4) {
            return "****";
        }
        return "*".repeat(Math.max(4, normalized.length() - 4)) + normalized.substring(normalized.length() - 4);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sanitize(String message) {
        String normalized = normalizeOptional(message);
        if (normalized == null) {
            return "Carrier request failed";
        }
        String sanitized = normalized.replaceAll("(?i)(api[_-]?key|authorization|token)\\s*[:=]\\s*[^,}\\s]+", "$1=***");
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String removeTrailingSlash(String value) {
        String normalized = normalizeRequired(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private List<String> collectAhamoveQuoteBlockingFields(CarrierConfig config) {
        List<String> missingFields = new ArrayList<>();
        if (!hasEncryptedValue(config.getApiKeyEnc())) {
            missingFields.add("apiKey");
        }
        if (normalizeOptional(config.getProviderAccountPhone()) == null) {
            missingFields.add("phone");
        }
        if (normalizeOptional(config.getPickupAddress()) == null) {
            missingFields.add("pickupAddress");
        }
        if (normalizeOptional(config.getPickupPhone()) == null) {
            missingFields.add("pickupPhone");
        }
        if (config.getPickupLat() == null) {
            missingFields.add("pickupLat");
        }
        if (config.getPickupLng() == null) {
            missingFields.add("pickupLng");
        }
        if (normalizeOptional(config.getDefaultServiceCode()) == null) {
            missingFields.add("defaultServiceCode");
        }
        return missingFields;
    }

    private String buildQuoteBlockedMessage(String prefix, List<String> missingQuoteFields) {
        return prefix + ": " + String.join(", ", missingQuoteFields);
    }

    private void updateConnectionHealth(
            CarrierConfig persistedConfig,
            CarrierConnectionStatus status,
            String errorMessage) {
        if (persistedConfig == null) {
            return;
        }
        persistedConfig.setConnectionStatus(status);
        persistedConfig.setLastHealthCheckAt(LocalDateTime.now());
        persistedConfig.setLastHealthCheckError(errorMessage);
        carrierConfigRepository.save(persistedConfig);
    }
}
