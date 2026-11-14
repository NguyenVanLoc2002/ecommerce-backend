package com.locnguyen.ecommerce.domains.catalogasset.dto;

import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogUploadPurpose;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateCatalogUploadRequest {
    @NotNull private CatalogUploadPurpose purpose;
    @NotBlank @Size(max = 255) private String originalFilename;
    @NotBlank @Size(max = 100) private String contentType;
    @Positive private long sizeBytes;
}
