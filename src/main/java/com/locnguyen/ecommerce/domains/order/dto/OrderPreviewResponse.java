package com.locnguyen.ecommerce.domains.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.carrier.dto.DeliveryEstimateResponse;
import com.locnguyen.ecommerce.domains.carrier.enums.CarrierProviderType;
import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import lombok.Builder;
import lombok.Getter;

import com.locnguyen.ecommerce.domains.returns.enums.ReturnPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderPreviewResponse {

    private final UUID carrierId;
    private final String carrierCode;
    private final String carrierName;
    private final CarrierProviderType carrierProviderType;
    private final String shippingServiceName;
    private final DeliveryEstimateResponse deliveryEstimate;
    private final PaymentMethod paymentMethod;
    private final BigDecimal subTotal;
    private final BigDecimal discountAmount;
    private final BigDecimal shippingFee;
    private final BigDecimal totalAmount;
    private final String voucherCode;
    private final String customerNote;
    private final List<ReturnPolicyLine> returnPolicies;

    public record ReturnPolicyLine(UUID variantId, ReturnPolicy policy) {}
}
