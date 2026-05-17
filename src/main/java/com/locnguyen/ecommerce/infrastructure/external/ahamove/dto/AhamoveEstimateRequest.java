package com.locnguyen.ecommerce.infrastructure.external.ahamove.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record AhamoveEstimateRequest(
        @JsonProperty("order_time")
        long orderTime,
        List<AhamovePathPoint> path,
        @JsonProperty("group_services")
        List<AhamoveGroupService> groupServices,
        @JsonProperty("payment_method")
        String paymentMethod,
        String remarks,
        List<AhamoveItem> items,
        @JsonProperty("package_detail")
        List<AhamovePackageDetail> packageDetail
) {
    @Builder
    public record AhamoveGroupService(
            @JsonProperty("_id")
            String id,
            @JsonProperty("group_requests")
            List<AhamoveRequestOption> groupRequests
    ) {}
}
