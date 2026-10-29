package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AhamovePathPoint {

    private BigDecimal lat;
    private BigDecimal lng;
    private String address;

    @JsonProperty("short_address")
    private String shortAddress;

    private String name;
    private String mobile;
    private BigDecimal cod;

    @JsonProperty("item_value")
    private BigDecimal itemValue;

    @JsonProperty("tracking_number")
    private String trackingNumber;

    private String remarks;
    private String status;

    @JsonProperty("fail_comment")
    private String failComment;

    @JsonProperty("complete_time")
    private Double completeTime;

    @JsonProperty("fail_time")
    private Double failTime;

    @JsonProperty("return_time")
    private Double returnTime;
}
