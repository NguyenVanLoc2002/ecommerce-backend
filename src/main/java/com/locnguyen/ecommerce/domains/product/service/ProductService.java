package com.locnguyen.ecommerce.domains.product.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.brand.entity.Brand;
import com.locnguyen.ecommerce.domains.brand.repository.BrandRepository;
import com.locnguyen.ecommerce.domains.category.entity.Category;
import com.locnguyen.ecommerce.domains.category.repository.CategoryRepository;
import com.locnguyen.ecommerce.domains.brand.mapper.BrandMapper;
import com.locnguyen.ecommerce.domains.category.mapper.CategoryMapper;
import com.locnguyen.ecommerce.domains.product.dto.*;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import com.locnguyen.ecommerce.domains.product.mapper.ProductMapper;
import com.locnguyen.ecommerce.domains.product.mapper.ProductVariantMapper;
import com.locnguyen.ecommerce.domains.product.repository.ProductRepository;
import com.locnguyen.ecommerce.domains.product.specification.ProductSpecification;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final BrandMapper brandMapper;
    private final CategoryMapper categoryMapper;
    private final ProductVariantMapper productVariantMapper;

    // ─── Public ───────────────────────────────────────────────────────────────

    /**
     * List published products with filtering, search, and pagination.
     * Returns lightweight list-item DTOs (no variant details).
     */
    @Transactional(readOnly = true)
    public PagedResponse<ProductListItemResponse> getPublishedProducts(ProductFilter filter, Pageable pageable) {
        ProductFilter publicFilter = ProductFilter.builder()
                .keyword(filter.getKeyword())
                .categoryId(filter.getCategoryId())
                .brandId(filter.getBrandId())
                .status(ProductStatus.PUBLISHED)
                .featured(filter.getFeatured())
                .minPrice(filter.getMinPrice())
                .maxPrice(filter.getMaxPrice())
                .build();

        Page<Product> page = productRepository.findAll(
                ProductSpecification.withFilter(publicFilter), pageable);

        return PagedResponse.of(page.map(productMapper::toListItem));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductById(Long id) {
        Product product = productRepository.findDetailById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() != ProductStatus.PUBLISHED) {
            throw new AppException(ErrorCode.PRODUCT_INACTIVE);
        }

        return toDetailResponse(product);
    }

    // ─── Admin CRUD ────────────────────────────────────────────────────────────

    @Transactional
    public ProductDetailResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySlug(request.getSlug())) {
            throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
        }

        Product product = new Product();
        applyProductFields(product, request);
        product.setStatus(request.getStatus() != null ? request.getStatus() : ProductStatus.DRAFT);
        product.setFeatured(Boolean.TRUE.equals(request.getFeatured()));

        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
            product.setBrand(brand);
        }
        if (request.getCategoryIds() != null) {
            setCategories(product, request.getCategoryIds());
        }

        product = productRepository.save(product);
        log.info("Product created: id={} name={}", product.getId(), product.getName());
        return toDetailResponse(product);
    }

    @Transactional
    public ProductDetailResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findOrThrow(id);

        if (request.getName() != null) product.setName(request.getName().trim());
        if (request.getShortDescription() != null) product.setShortDescription(request.getShortDescription().trim());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getStatus() != null) product.setStatus(request.getStatus());
        if (request.getFeatured() != null) product.setFeatured(request.getFeatured());

        if (request.getSlug() != null) {
            String newSlug = request.getSlug().trim();
            if (!newSlug.equals(product.getSlug()) && productRepository.existsBySlug(newSlug)) {
                throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
            }
            product.setSlug(newSlug);
        }

        if (request.getBrandId() != null) {
            if (request.getBrandId() == null) {
                product.setBrand(null);
            } else {
                Brand brand = brandRepository.findById(request.getBrandId())
                        .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
                product.setBrand(brand);
            }
        }

        if (request.getCategoryIds() != null) {
            setCategories(product, request.getCategoryIds());
        }

        product = productRepository.save(product);
        log.info("Product updated: id={}", id);
        return toDetailResponse(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findOrThrow(id);
        String actor = com.locnguyen.ecommerce.common.utils.SecurityUtils.getCurrentUsernameOrSystem();
        product.softDelete(actor);
        productRepository.save(product);
        log.info("Product deleted: id={} by={}", id, actor);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductListItemResponse> getAllProducts(ProductFilter filter, Pageable pageable) {
        Page<Product> page = productRepository.findAll(
                ProductSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(productMapper::toListItem));
    }

    // ─── Variant management ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetailAdmin(Long id) {
        return toDetailResponse(findOrThrow(id));
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private void setCategories(Product product, java.util.List<Long> categoryIds) {
        product.getCategories().clear();
        if (categoryIds != null && !categoryIds.isEmpty()) {
            List<Category> categories = categoryRepository.findAllById(categoryIds);
            product.getCategories().addAll(categories);
        }
    }

    private void applyProductFields(Product product, CreateProductRequest request) {
        product.setName(request.getName().trim());
        product.setSlug(request.getSlug().trim());
        product.setShortDescription(request.getShortDescription());
        product.setDescription(request.getDescription());
    }

    private ProductDetailResponse toDetailResponse(Product product) {
        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .shortDescription(product.getShortDescription())
                .description(product.getDescription())
                .status(product.getStatus().name())
                .featured(product.isFeatured())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .brand(product.getBrand() != null
                        ? brandMapper.toResponse(product.getBrand())
                        : null)
                .categories(product.getCategories().stream()
                        .map(categoryMapper::toResponse)
                        .toList())
                .variants(product.getVariants().stream()
                        .map(productVariantMapper::toResponse)
                        .toList())
                .media(product.getMedia().stream()
                        .map(m -> MediaResponse.builder()
                                .id(m.getId())
                                .mediaUrl(m.getMediaUrl())
                                .mediaType(m.getMediaType().name())
                                .sortOrder(m.getSortOrder())
                                .primary(m.isPrimary())
                                .variantId(m.getVariant() != null ? m.getVariant().getId() : null)
                                .build())
                        .toList())
                .build();
    }
}
