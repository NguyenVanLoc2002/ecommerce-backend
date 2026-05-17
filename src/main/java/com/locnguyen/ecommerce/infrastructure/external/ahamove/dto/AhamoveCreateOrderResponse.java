package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AhamoveCreateOrderResponse {

    @JsonProperty("order_id")
    private String orderId;

    private String status;

    @JsonProperty("shared_link")
    private String sharedLink;

    private AhamoveOrderPayload order;
}
