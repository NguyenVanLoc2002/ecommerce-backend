package com.locnguyen.ecommerce.domains.promotion.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.promotion.dto.ValidateVoucherRequest;
import com.locnguyen.ecommerce.domains.promotion.dto.ValidateVoucherResponse;
import com.locnguyen.ecommerce.domains.promotion.service.VoucherService;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Vouchers", description = "Customer-facing voucher validation")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/vouchers")
public class VoucherController {

    private final VoucherService voucherService;
    private final UserService userService;

    @Operation(
            summary = "Validate a voucher code and preview the discount",
            description = "Returns the calculated discount amount for the given order context. " +
                    "Does NOT record usage — call this during checkout to preview the discount " +
                    "before order submission."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{code}/validate")
    public ApiResponse<ValidateVoucherResponse> validate(
            @PathVariable String code,
            @Valid @RequestBody ValidateVoucherRequest request) {
        return ApiResponse.success(
                voucherService.validateVoucher(code, userService.getCurrentCustomer(), request));
    }
}
