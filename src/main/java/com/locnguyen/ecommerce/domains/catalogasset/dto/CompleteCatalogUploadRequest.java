package com.locnguyen.ecommerce.domains.catalogasset.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CompleteCatalogUploadRequest {
    @NotBlank @Size(max = 500) private String storageKey;
}
