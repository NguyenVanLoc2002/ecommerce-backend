package com.locnguyen.ecommerce.domains.product.service;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.brand.entity.Brand;
import com.locnguyen.ecommerce.domains.brand.mapper.BrandMapper;
import com.locnguyen.ecommerce.domains.brand.repository.BrandRepository;
import com.locnguyen.ecommerce.domains.category.entity.Category;
import com.locnguyen.ecommerce.domains.category.mapper.CategoryMapper;
import com.locnguyen.ecommerce.domains.category.repository.CategoryRepository;
import com.locnguyen.ecommerce.domains.product.dto.*;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import com.locnguyen.ecommerce.domains.product.mapper.ProductMapper;
import com.locnguyen.ecommerce.domains.product.mapper.ProductVariantMapper;
import com.locnguyen.ecommerce.domains.product.repository.ProductRepository;
import com.locnguyen.ecommerce.domains.product.specification.ProductSpecification;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
    private final AuditLogService auditLogService;

    // ─── Public ───────────────────────────────────────────────────────────────

    /**
     * List published products with dynamic filters and pagination.
     * Not cached — too many filter/page combinations would fill Redis with one-shot keys.
     * The DB query is optimised via {@code @EntityGraph} in the repository.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ProductListItemResponse> getPublishedProducts(ProductFilter filter,
                                                                       Pageable pageable) {
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

    /**
     * Get published product detail by ID.
     * Cached for 5 minutes under {@code product_detail::{id}}.
     * Evicted on any product mutation (create / update / delete).
     */
    @Cacheable(value = AppConstants.CACHE_PRODUCT_DETAIL, key = "#id")
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

    @Caching(evict = {
            @CacheEvict(value = AppConstants.CACHE_PRODUCT_DETAIL, allEntries = true),
            @CacheEvict(value = AppConstants.CACHE_PRODUCTS, allEntries = true)
    })
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
        auditLogService.log(AuditAction.PRODUCT_CREATED, "PRODUCT",
                String.valueOf(product.getId()), "name=" + product.getName());
        return toDetailResponse(product);
    }

    @Caching(evict = {
            @CacheEvict(value = AppConstants.CACHE_PRODUCT_DETAIL, key = "#id"),
            @CacheEvict(value = AppConstants.CACHE_PRODUCTS, allEntries = true)
    })
    @Transactional
    public ProductDetailResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findOrThrow(id);

        if (request.getName() != null) product.setName(request.getName().trim());
        if (request.getShortDescription() != null) product.setShortDescription(request.getShortDescription().trim());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            boolean publishing = request.getStatus() == ProductStatus.PUBLISHED
                    && product.getStatus() != ProductStatus.PUBLISHED;
            product.setStatus(request.getStatus());
            if (publishing) {
                auditLogService.log(AuditAction.PRODUCT_PUBLISHED, "PRODUCT", String.valueOf(id));
            }
        }
        if (request.getFeatured() != null) product.setFeatured(request.getFeatured());

        if (request.getSlug() != null) {
            String newSlug = request.getSlug().trim();
            if (!newSlug.equals(product.getSlug()) && productRepository.existsBySlug(newSlug)) {
                throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
            }
            product.setSlug(newSlug);
        }

        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
            product.setBrand(brand);
        }

        if (request.getCategoryIds() != null) {
            setCategories(product, request.getCategoryIds());
        }

        product = productRepository.save(product);
        log.info("Product updated: id={}", id);
        auditLogService.log(AuditAction.PRODUCT_UPDATED, "PRODUCT", String.valueOf(id));
        return toDetailResponse(product);
    }

    @Caching(evict = {
            @CacheEvict(value = AppConstants.CACHE_PRODUCT_DETAIL, key = "#id"),
            @CacheEvict(value = AppConstants.CACHE_PRODUCTS, allEntries = true)
    })
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findOrThrow(id);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        product.softDelete(actor);
        productRepository.save(product);
        log.info("Product deleted: id={} by={}", id, actor);
        auditLogService.log(AuditAction.PRODUCT_DELETED, "PRODUCT", String.valueOf(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProductListItemResponse> getAllProducts(ProductFilter filter,
                                                                 Pageable pageable) {
        Page<Product> page = productRepository.findAll(
                ProductSpecification.withFilter(filter), pageable);
        return PagedResponse.of(page.map(productMapper::toListItem));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetailAdmin(Long id) {
        return toDetailResponse(findOrThrow(id));
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private void setCategories(Product product, List<Long> categoryIds) {
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
                .status(product.getStatus())
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
