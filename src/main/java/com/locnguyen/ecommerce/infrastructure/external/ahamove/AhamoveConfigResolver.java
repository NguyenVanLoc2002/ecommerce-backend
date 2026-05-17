package com.locnguyen.ecommerce.infrastructure.external.ahamove;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.security.AesGcmTextCipher;
import com.locnguyen.ecommerce.domains.carrier.entity.CarrierConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AhamoveConfigResolver {

    private final AesGcmTextCipher textCipher;
    private final AhamoveProperties properties;
    private final ObjectMapper objectMapper;

    public AhamoveResolvedConfig resolve(CarrierConfig config) {
        return resolve(config, true);
    }

    public AhamoveResolvedConfig resolveAllowDisabled(CarrierConfig config) {
        return resolve(config, false);
    }

    private AhamoveResolvedConfig resolve(CarrierConfig config, boolean requireEnabled) {
        if (config == null) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                    "AhaMove carrier config is missing");
        }
        if (requireEnabled && !config.isEnabled()) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_DISABLED,
                    "AhaMove carrier config is disabled");
        }

        AhamoveConfigData data = parseConfigData(config.getConfigJson());
        String baseUrl = firstNonBlank(config.getBaseUrl(), properties.getBaseUrl());
        String apiKey = firstNonBlank(decrypt(config.getApiKeyEnc()), properties.getApiKey());
        String phone = firstNonBlank(config.getProviderAccountPhone(), data.getPhone(), properties.getPhone());
        String brandName = firstNonBlank(config.getProviderBrandName(), data.getBrandName(), properties.getBrandName());
        String webhookToken = firstNonBlank(decrypt(config.getWebhookSecretEnc()),
                data.getWebhookToken(), properties.getWebhookToken());
        String pickupAddress = firstNonBlank(config.getPickupAddress(), data.getPickupAddress());
        String pickupShortAddress = firstNonBlank(config.getPickupShortAddress(), data.getPickupShortAddress());
        String pickupName = firstNonBlank(config.getPickupName(), data.getPickupName(), brandName);
        String pickupPhone = firstNonBlank(config.getPickupPhone(), data.getPickupPhone(), phone);
        String groupServiceId = firstNonBlank(config.getDefaultServiceCode(), data.getGroupServiceId(),
                properties.getGroupServiceId());
        String paymentMethod = firstNonBlank(config.getDefaultPaymentMethod(), data.getPaymentMethod(),
                properties.getPaymentMethod());
        List<AhamoveConfigData.RequestOption> groupRequests = data.getGroupRequests() != null
                ? data.getGroupRequests()
                : new ArrayList<>();

        if (isBlank(baseUrl)) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                    "AhaMove baseUrl is not configured");
        }
        if (isBlank(apiKey)) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                    "AhaMove apiKey is not configured");
        }
        if (isBlank(phone)) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                    "AhaMove phone is not configured");
        }
        if (isBlank(pickupAddress)) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                    "AhaMove pickupAddress must be configured in carrier configJson");
        }

        return new AhamoveResolvedConfig(
                normalizeBaseUrl(baseUrl),
                apiKey.trim(),
                phone.trim(),
                brandName != null ? brandName.trim() : null,
                trimToNull(webhookToken),
                pickupAddress.trim(),
                trimToNull(pickupShortAddress),
                pickupName != null ? pickupName.trim() : null,
                pickupPhone != null ? pickupPhone.trim() : null,
                firstNonNull(config.getPickupLat(), data.getPickupLat()),
                firstNonNull(config.getPickupLng(), data.getPickupLng()),
                groupServiceId != null ? groupServiceId.trim() : null,
                paymentMethod != null ? paymentMethod.trim() : null,
                groupRequests
        );
    }

    private AhamoveConfigData parseConfigData(String configJson) {
        if (isBlank(configJson)) {
            return new AhamoveConfigData();
        }
        try {
            return objectMapper.readValue(configJson, AhamoveConfigData.class);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.CARRIER_CONFIG_MISSING,
                    "AhaMove configJson is invalid");
        }
    }

    private String decrypt(String encryptedValue) {
        if (isBlank(encryptedValue)) {
            return null;
        }
        return textCipher.decrypt(encryptedValue);
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v3")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            String normalized = trimToNull(candidate);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }
}
