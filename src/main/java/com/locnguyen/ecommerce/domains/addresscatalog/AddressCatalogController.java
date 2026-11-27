package com.locnguyen.ecommerce.domains.addresscatalog;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AppConstants.API_V1 + "/locations/provinces")
@RequiredArgsConstructor
public class AddressCatalogController {
    private final AddressCatalog catalog;

    @GetMapping
    public ApiResponse<List<AddressCatalog.Province>> provinces() {
        return ApiResponse.success(catalog.provinces());
    }

    @GetMapping("/{provinceCode}/wards")
    public ApiResponse<List<AddressCatalog.Ward>> wards(@PathVariable @Pattern(regexp = "[0-9]{2}") String provinceCode) {
        return ApiResponse.success(catalog.wards(provinceCode));
    }
}
