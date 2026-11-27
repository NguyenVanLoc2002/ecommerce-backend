package com.locnguyen.ecommerce.domains.shippingpolicy;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.shippingpolicy.ShippingPolicyModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_V1)
@RequiredArgsConstructor
public class ShippingPolicyController {
    private static final String ADMIN_PATH = "/admin/policies/shipping";
    private static final String ADMIN_ROLE = "hasAnyRole('ADMIN', 'SUPER_ADMIN')";
    private final ShippingPolicyService service;

    @GetMapping("/policies/shipping")
    public ApiResponse<PublicPolicy> published() {
        return ApiResponse.success(service.published());
    }

    @GetMapping(ADMIN_PATH + "/capabilities")
    @PreAuthorize(ADMIN_ROLE)
    public ApiResponse<Capabilities> capabilities() {
        return ApiResponse.success(service.capabilities());
    }

    @GetMapping(ADMIN_PATH)
    @PreAuthorize(ADMIN_ROLE)
    public ApiResponse<Page> list(@RequestParam(defaultValue = "0") @Min(0) int page) {
        return ApiResponse.success(service.list(page));
    }

    @GetMapping(ADMIN_PATH + "/{version}")
    @PreAuthorize(ADMIN_ROLE)
    public ApiResponse<Policy> get(@PathVariable long version) {
        return ApiResponse.success(service.get(version));
    }

    @PostMapping(ADMIN_PATH)
    @PreAuthorize(ADMIN_ROLE)
    public ApiResponse<Policy> create(@RequestBody @Valid Draft input) {
        return ApiResponse.created(service.create(input));
    }

    @PutMapping(ADMIN_PATH + "/{version}")
    @PreAuthorize(ADMIN_ROLE)
    public ApiResponse<Policy> edit(@PathVariable long version, @RequestBody @Valid Edit input) {
        return ApiResponse.success(service.edit(version, input));
    }

    @PostMapping(ADMIN_PATH + "/{version}/publish")
    @PreAuthorize(ADMIN_ROLE)
    public ApiResponse<Policy> publish(@PathVariable long version, @RequestBody @Valid Revision input) {
        return ApiResponse.success(service.publish(version, input.revision()));
    }

    @PostMapping(ADMIN_PATH + "/{version}/archive")
    @PreAuthorize(ADMIN_ROLE)
    public ApiResponse<Policy> archive(@PathVariable long version, @RequestBody @Valid Revision input) {
        return ApiResponse.success(service.archive(version, input.revision()));
    }

    @GetMapping(ADMIN_PATH + "/{version}/history")
    @PreAuthorize(ADMIN_ROLE)
    public ApiResponse<HistoryPage> history(@PathVariable long version,
                                           @RequestParam(defaultValue = "0") @Min(0) int page) {
        return ApiResponse.success(service.history(version, page));
    }
}
