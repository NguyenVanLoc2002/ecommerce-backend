package com.locnguyen.ecommerce.domains.brand.dto;

import com.locnguyen.ecommerce.domains.brand.enums.BrandStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Brand filter parameters")
public class BrandFilter {

    @Schema(description = "Partial, case-insensitive match on brand name", example = "nike")
    private String name;

    @Schema(description = "Filter by status: ACTIVE or INACTIVE", example = "ACTIVE")
    private BrandStatus status;

    @Schema(description = "Soft delete filter: false=active only, true=deleted only", example = "false")
    private Boolean isDeleted;

    @Schema(description = "Include both active and deleted rows", example = "false")
    private Boolean includeDeleted;
}
