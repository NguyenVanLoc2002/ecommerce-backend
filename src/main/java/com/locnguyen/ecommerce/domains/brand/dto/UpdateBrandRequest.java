package com.locnguyen.ecommerce.domains.brand.dto;

import com.locnguyen.ecommerce.domains.brand.enums.BrandStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Update brand request — only provided fields are updated")
public class UpdateBrandRequest {

    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String slug;

    private String logoUrl;

    private String description;

    private BrandStatus status;
}
