package com.locnguyen.ecommerce.domains.order.dto;

import com.locnguyen.ecommerce.domains.order.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Create order request — checkout from cart")
public class CreateOrderRequest {

    @NotNull(message = "Shipping address ID is required")
    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long shippingAddressId;

    @Schema(example = "COD", description = "Payment method: COD or ONLINE", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private PaymentMethod paymentMethod;

    @Schema(example = "Giao giờ hành chính")
    @Size(max = 500)
    private String customerNote;

    @Schema(example = "SALE20", description = "Voucher code to apply (optional)")
    private String voucherCode;
}
