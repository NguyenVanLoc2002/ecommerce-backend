package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AhamoveEstimateOptionResponse {

    @JsonProperty("service_id")
    private String serviceId;

    private EstimateData data;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EstimateData {
        private BigDecimal distance;
        private Integer duration;

        @JsonProperty("distance_fee")
        private BigDecimal distanceFee;

        @JsonProperty("request_fee")
        private BigDecimal requestFee;

        @JsonProperty("total_fee")
        private BigDecimal totalFee;

        @JsonProperty("total_price")
        private BigDecimal totalPrice;
    }
}
