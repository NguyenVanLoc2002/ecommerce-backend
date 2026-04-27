package com.locnguyen.ecommerce.domains.brand.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.brand.enums.BrandStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Brand response")
public class BrandResponse {

    private final Long id;
    private final String name;
    private final String slug;
    private final String logoUrl;
    private final String description;
    private final Integer sortOrder;
    private final BrandStatus status;
    private final LocalDateTime createdAt;
}
