package com.locnguyen.ecommerce.domains.order.service.tax;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.order.service.tax.TaxInputs.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/tax")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class TaxConfigurationController {
    private final TaxConfigurationService service;
    private final OrderTaxEstimates estimates;

    @GetMapping("/categories")
    public ApiResponse<List<Category>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/categories/{code}")
    public ApiResponse<Schedule> get(@PathVariable String code) {
        return ApiResponse.success(service.get(code));
    }

    @PostMapping("/categories")
    public ApiResponse<Category> create(@RequestBody @Valid CategoryInput input) {
        return ApiResponse.created(service.create(input));
    }

    @PutMapping("/categories/{code}")
    public ApiResponse<Category> update(@PathVariable String code, @RequestBody @Valid CategoryUpdate input) {
        return ApiResponse.success(service.update(code, input));
    }

    @PutMapping("/categories/{code}/schedule")
    public ApiResponse<Schedule> configure(@PathVariable String code, @RequestBody @Valid ScheduleUpdate input) {
        return ApiResponse.success(service.configure(code, input));
    }

    @GetMapping("/assignments/{productId}")
    public ApiResponse<Assignment> assignment(@PathVariable UUID productId) {
        return ApiResponse.success(service.assignment(productId));
    }

    @PutMapping("/assignments/{productId}")
    public ApiResponse<Assignment> assign(@PathVariable UUID productId, @RequestBody @Valid Assignment input) {
        return ApiResponse.success(service.assign(productId, input));
    }

    @GetMapping("/audit/{subject}")
    public ApiResponse<List<ConfigAudit>> audit(@PathVariable String subject,
                                               @RequestParam(defaultValue = "0") @Min(0) long before) {
        return ApiResponse.success(service.audit(subject, before));
    }

    @GetMapping("/orders/{orderId}/estimate")
    public ApiResponse<OrderTaxEstimates.Estimate> estimate(@PathVariable UUID orderId) {
        service.requireConfigurator();
        return ApiResponse.success(estimates.get(orderId));
    }

    @GetMapping("/orders/{orderId}/final")
    public ApiResponse<OrderTaxEstimates.FinalSnapshot> finalized(@PathVariable UUID orderId) {
        service.requireConfigurator();
        return ApiResponse.success(estimates.getFinal(orderId));
    }
}
