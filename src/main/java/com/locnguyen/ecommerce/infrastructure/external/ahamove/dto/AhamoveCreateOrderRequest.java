package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record AhamoveCreateOrderRequest(
        @JsonProperty("order_time")
        long orderTime,
        List<AhamovePathPoint> path,
        @JsonProperty("group_service_id")
        String groupServiceId,
        @JsonProperty("group_requests")
        List<AhamoveRequestOption> groupRequests,
        @JsonProperty("payment_method")
        String paymentMethod,
        String remarks,
        List<AhamoveItem> items,
        @JsonProperty("package_detail")
        List<AhamovePackageDetail> packageDetail
) {}
