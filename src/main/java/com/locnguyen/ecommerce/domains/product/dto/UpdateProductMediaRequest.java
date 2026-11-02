package com.locnguyen.ecommerce.domains.product.dto;

import com.locnguyen.ecommerce.domains.product.enums.MediaType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateProductMediaRequest {

    @Size(max = 500)
    @Pattern(regexp = "https?://\\S+", message = "Media URL must use http or https")
    private String mediaUrl;

    private MediaType mediaType;

    @Min(value = 0, message = "Sort order must not be negative")
    private Integer sortOrder;

    private Boolean primary;

    private UUID variantId;
    private boolean variantIdProvided;

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
        this.variantIdProvided = true;
    }
}
