package com.locnguyen.ecommerce.domains.sizeguide.controller;

import com.locnguyen.ecommerce.domains.sizeguide.dto.SizeGuideModels.*;
import com.locnguyen.ecommerce.domains.sizeguide.service.SizeGuideService;
import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_V1 + "/admin/size-guides")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminSizeGuideController {
    private final SizeGuideService service;

    @GetMapping
    public ApiResponse<PagedResponse<Summary>> list(@RequestParam(defaultValue = "") String search,
                                                    @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(service.list(search, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<Guide> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    public ApiResponse<Guide> create(@RequestBody @Valid Input input) {
        return ApiResponse.success(service.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<Guide> update(@PathVariable UUID id, @RequestBody @Valid Input input) {
        return ApiResponse.success(service.update(id, input));
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<Guide> copy(@PathVariable UUID id) {
        return ApiResponse.success(service.copy(id));
    }

    @GetMapping("/{id}/assignments")
    public ApiResponse<List<Assignment>> assignments(@PathVariable UUID id) {
        return ApiResponse.success(service.assignments(id));
    }

    @PutMapping("/{id}/assignments")
    public ApiResponse<Void> assign(@PathVariable UUID id, @RequestBody @Valid AssignmentInput input) {
        service.assign(id, input);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/assignments/{key}")
    public ApiResponse<Void> unassign(@PathVariable UUID id, @PathVariable String key) {
        service.unassign(id, key);
        return ApiResponse.success(null);
    }
}
