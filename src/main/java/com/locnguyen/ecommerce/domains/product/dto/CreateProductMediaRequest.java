package com.locnguyen.ecommerce.domains.product.dto;

import com.locnguyen.ecommerce.domains.product.enums.MediaType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateProductMediaRequest {

    @Size(max = 500)
    @Pattern(regexp = "https?://\\S+", message = "Media URL must use http or https")
    private String mediaUrl;

    @Size(max = 500)
    private String storageKey;

    private MediaType mediaType = MediaType.IMAGE;

    @Min(value = 0, message = "Sort order must not be negative")
    private Integer sortOrder = 0;

    private boolean primary;

    private UUID variantId;
}
