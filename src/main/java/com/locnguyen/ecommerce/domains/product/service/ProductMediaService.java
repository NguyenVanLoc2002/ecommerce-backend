package com.locnguyen.ecommerce.domains.product.service;

import com.locnguyen.ecommerce.domains.product.dto.CreateProductMediaRequest;
import com.locnguyen.ecommerce.domains.product.dto.MediaResponse;
import com.locnguyen.ecommerce.domains.product.dto.UpdateProductMediaRequest;

import java.util.UUID;

public interface ProductMediaService {

    MediaResponse createMedia(UUID productId, CreateProductMediaRequest request);

    MediaResponse updateMedia(UUID productId, UUID mediaId, UpdateProductMediaRequest request);

    void deleteMedia(UUID productId, UUID mediaId);
}
