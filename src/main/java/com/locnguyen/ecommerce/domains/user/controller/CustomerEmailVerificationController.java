package com.locnguyen.ecommerce.domains.user.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.user.dto.ConfirmEmailVerificationRequest;
import com.locnguyen.ecommerce.domains.user.service.CustomerEmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AppConstants.API_V1 + "/account/email-verification")
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CustomerEmailVerificationController {

    private final CustomerEmailVerificationService emailVerificationService;

    @Operation(summary = "Send a verification code to the authenticated customer's email")
    @PostMapping
    public ApiResponse<Void> requestVerification() {
        emailVerificationService.requestVerification();
        return ApiResponse.noContent();
    }

    @Operation(summary = "Verify the authenticated customer's email")
    @PostMapping("/confirm")
    public ApiResponse<Void> confirmVerification(@Valid @RequestBody ConfirmEmailVerificationRequest request) {
        emailVerificationService.confirmVerification(request.otp());
        return ApiResponse.noContent();
    }
}
