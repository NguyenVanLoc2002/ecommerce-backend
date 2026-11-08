package com.locnguyen.ecommerce.domains.product.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.product.dto.CreateProductRequest;
import com.locnguyen.ecommerce.domains.product.dto.ProductDetailResponse;
import com.locnguyen.ecommerce.domains.product.dto.ProductFilter;
import com.locnguyen.ecommerce.domains.product.dto.ProductListItemResponse;
import com.locnguyen.ecommerce.domains.product.dto.UpdateProductRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {

    PagedResponse<ProductListItemResponse> getPublishedProducts(ProductFilter filter, Pageable pageable);

    ProductDetailResponse getProductById(UUID id);

    ProductDetailResponse getProductBySlug(String slug);

    ProductDetailResponse createProduct(CreateProductRequest request);

    ProductDetailResponse updateProduct(UUID id, UpdateProductRequest request);

    void deleteProduct(UUID id);

    PagedResponse<ProductListItemResponse> getAllProducts(ProductFilter filter, Pageable pageable);

    ProductDetailResponse getProductDetailAdmin(UUID id);
}
