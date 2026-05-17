package com.locnguyen.ecommerce.infrastructure.external.ahamove;

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
public class AhamoveConfigData {

    private String phone;
    private String brandName;
    private String webhookToken;
    private String pickupAddress;
    private String pickupShortAddress;
    private String pickupName;
    private String pickupPhone;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private String groupServiceId;
    private String paymentMethod;
    private List<RequestOption> groupRequests = new ArrayList<>();

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequestOption {

        @JsonProperty("_id")
        private String id;

        private Integer num;

        @JsonProperty("tier_code")
        private String tierCode;
    }
}
