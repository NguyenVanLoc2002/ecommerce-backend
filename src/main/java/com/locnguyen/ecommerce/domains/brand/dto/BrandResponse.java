package com.locnguyen.ecommerce.domains.brand.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.locnguyen.ecommerce.domains.brand.enums.BrandStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;

import java.util.UUID;
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Brand response")
public class BrandResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String name;
    private final String slug;
    private final String logoUrl;
    private final String logoStorageKey;
    private final String description;
    private final Integer sortOrder;
    private final BrandStatus status;
    private final LocalDateTime createdAt;
}
