package com.locnguyen.ecommerce.domains.carrier.calendar;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.carrier.calendar.ShippingCalendarModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/shipping/calendars")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class ShippingCalendarController {
    private final ShippingCalendarService service;

    @GetMapping
    public ApiResponse<CalendarPage> list(@RequestParam(defaultValue = "0") @Min(0) int page) {
        return ApiResponse.success(service.list(page));
    }

    @GetMapping("/{id}")
    public ApiResponse<Calendar> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    public ApiResponse<Calendar> create(@Valid @RequestBody Input input) {
        return ApiResponse.created(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Calendar> edit(@PathVariable UUID id, @Valid @RequestBody Edit input) {
        return ApiResponse.success(service.edit(id, input));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<HistoryPage> history(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page) {
        return ApiResponse.success(service.history(id, page));
    }

    @PostMapping("/{id}/preview-processing")
    public ApiResponse<ProcessingStart> previewProcessing(@PathVariable UUID id, @Valid @RequestBody ProcessingInput input) {
        return ApiResponse.success(service.previewProcessing(id, input));
    }

    @PostMapping("/{id}/preview-delivery")
    public ApiResponse<DeliveryWindow> previewDelivery(@PathVariable UUID id, @Valid @RequestBody DeliveryInput input) {
        return ApiResponse.success(service.previewDelivery(id, input));
    }
}
