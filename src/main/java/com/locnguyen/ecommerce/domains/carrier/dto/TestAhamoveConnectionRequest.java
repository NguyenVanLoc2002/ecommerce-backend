package com.locnguyen.ecommerce.domains.carrier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TestAhamoveConnectionRequest {

    @Size(max = 500)
    private String apiKey;

    @Size(max = 500)
    private String baseUrl;

    @Size(max = 50)
    private String phone;

    @Size(max = 100)
    private String probeReceiverName;

    @Size(max = 50)
    private String probeReceiverPhone;

    @Size(max = 255)
    private String probeShippingStreet;

    @Size(max = 100)
    private String probeShippingWard;

    @Size(max = 100)
    private String probeShippingDistrict;

    @Size(max = 100)
    private String probeShippingCity;

    @DecimalMin("0.0")
    private BigDecimal probeSubTotal;
}
