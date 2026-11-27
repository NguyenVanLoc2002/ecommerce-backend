package com.locnguyen.ecommerce.domains.carrier.estimate;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.carrier.estimate.DeliveryEstimateModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/shipping/delivery-rules")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class DeliveryEstimateRuleController {
    private final DeliveryEstimateRuleService service;

    @GetMapping
    public ApiResponse<RulePage> list(@RequestParam UUID carrierId, @RequestParam(defaultValue = "0") @Min(0) int page) {
        return ApiResponse.success(service.list(carrierId, page));
    }

    @GetMapping("/{id}")
    public ApiResponse<Rule> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    public ApiResponse<Rule> create(@Valid @RequestBody Create input) {
        return ApiResponse.created(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Rule> edit(@PathVariable UUID id, @Valid @RequestBody Edit input) {
        return ApiResponse.success(service.edit(id, input));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<HistoryPage> history(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page) {
        return ApiResponse.success(service.history(id, page));
    }

    @GetMapping("/calendar-options")
    public ApiResponse<List<CalendarOption>> calendarOptions() {
        return ApiResponse.success(service.calendarOptions());
    }

    @PostMapping("/preview")
    public ApiResponse<EstimateResult> preview(@Valid @RequestBody Preview input) {
        return ApiResponse.success(new EstimateResult(service.preview(input).orElse(null)));
    }
}
