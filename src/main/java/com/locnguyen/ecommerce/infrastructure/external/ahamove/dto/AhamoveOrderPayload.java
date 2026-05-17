package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AhamoveOrderPayload {

    @JsonProperty("_id")
    private String id;

    private String status;

    @JsonProperty("sub_status")
    private String subStatus;

    @JsonProperty("service_id")
    private String serviceId;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("shared_link")
    private String sharedLink;

    @JsonProperty("accept_time")
    private Double acceptTime;

    @JsonProperty("board_time")
    private Double boardTime;

    @JsonProperty("pickup_time")
    private Double pickupTime;

    @JsonProperty("complete_time")
    private Double completeTime;

    @JsonProperty("cancel_time")
    private Double cancelTime;

    @JsonProperty("cancel_by_user")
    private Boolean cancelByUser;

    @JsonProperty("cancel_comment")
    private String cancelComment;

    @JsonProperty("total_pay")
    private BigDecimal totalPay;

    private Integer duration;
    private BigDecimal distance;
    private List<AhamovePathPoint> path = new ArrayList<>();
}
