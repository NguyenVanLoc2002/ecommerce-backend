package com.locnguyen.ecommerce.domains.carrier.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierConnectionStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AhamoveConnectionTestResponse {
    private final boolean success;
    private final CarrierConnectionStatus status;
    private final String message;
    private final String resolvedBaseUrl;
    private final String resolvedPhone;
}
