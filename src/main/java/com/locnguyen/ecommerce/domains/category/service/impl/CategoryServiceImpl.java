package com.locnguyen.ecommerce.domains.category.service.impl;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.category.dto.CategoryFilter;
import com.locnguyen.ecommerce.domains.category.dto.CategoryResponse;
import com.locnguyen.ecommerce.domains.category.dto.CreateCategoryRequest;
import com.locnguyen.ecommerce.domains.category.dto.UpdateCategoryRequest;
import com.locnguyen.ecommerce.domains.category.entity.Category;
import com.locnguyen.ecommerce.domains.category.enums.CategoryStatus;
import com.locnguyen.ecommerce.domains.category.mapper.CategoryMapper;
import com.locnguyen.ecommerce.domains.category.repository.CategoryRepository;
import com.locnguyen.ecommerce.domains.category.service.CategoryService;
import com.locnguyen.ecommerce.domains.category.specification.CategorySpecification;
import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogUploadPurpose;
import com.locnguyen.ecommerce.domains.catalogasset.service.CatalogAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final AuditLogService auditLogService;
    private final CatalogAssetService catalogAssetService;

    @Override
    public PagedResponse<CategoryResponse> getAllCategories(CategoryFilter filter, Pageable pageable) {
        Page<Category> pageCategories = categoryRepository.findAll(
                CategorySpecification.withFilter(filter), pageable
        );
        return PagedResponse.of(pageCategories.map(categoryMapper::toResponse));
    }

    @Override
    @Cacheable(value = AppConstants.CACHE_CATEGORIES, key = "'active'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByStatusOrderBySortOrderAsc(CategoryStatus.ACTIVE)
                .stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    @Cacheable(value = AppConstants.CACHE_CATEGORIES, key = "'id:' + #id")
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        return categoryMapper.toResponse(findOrThrow(id));
    }

    @Override
    @CacheEvict(value = AppConstants.CACHE_CATEGORIES, allEntries = true)
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
        }

        Category category = new Category();
        category.setName(request.getName().trim());
        category.setReturnPolicy(request.getReturnPolicy());
        category.setSlug(request.getSlug().trim());
        category.setDescription(request.getDescription());
        if (hasValue(request.getImageStorageKey()) && hasValue(request.getImageUrl())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Provide either imageUrl or imageStorageKey");
        }
        if (hasValue(request.getImageStorageKey())) {
            var asset = catalogAssetService.claimVerifiedAsset(request.getImageStorageKey().trim(), CatalogUploadPurpose.CATEGORY_IMAGE);
            category.setImageUrl(asset.mediaUrl());
            category.setAssetStorageKey(asset.storageKey());
        } else {
            category.setImageUrl(request.getImageUrl());
        }
        category.setStatus(CategoryStatus.ACTIVE);
        category.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        if (request.getParentId() != null) {
            Category parent = categoryRepository.findByIdAndDeletedFalse(request.getParentId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            category.setParent(parent);
        }

        category = categoryRepository.save(category);
        if (category.getAssetStorageKey() != null) catalogAssetService.associateAsset(category.getAssetStorageKey(), CatalogUploadPurpose.CATEGORY_IMAGE, "CATEGORY", category.getId());
        log.info("Category created: id={} name={}", category.getId(), category.getName());
        auditLogService.log(AuditAction.CATEGORY_CREATED, "CATEGORY",
                String.valueOf(category.getId()), "name=" + category.getName());
        return categoryMapper.toResponse(category);
    }

    @Override
    @CacheEvict(value = AppConstants.CACHE_CATEGORIES, allEntries = true)
    @Transactional
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        Category category = findOrThrow(id);

        if (request.getReturnPolicy() != null) {
            category.setReturnPolicy(request.getReturnPolicy());
        }
        if (request.getName() != null) {
            category.setName(request.getName().trim());
        }
        if (request.getSlug() != null) {
            String newSlug = request.getSlug().trim();
            if (!newSlug.equals(category.getSlug()) && categoryRepository.existsBySlug(newSlug)) {
                throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
            }
            category.setSlug(newSlug);
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        String previousAsset = category.getAssetStorageKey();
        boolean sameAsset = hasValue(request.getImageStorageKey()) && request.getImageStorageKey().trim().equals(previousAsset);
        if (hasValue(request.getImageStorageKey()) && !sameAsset) {
            if (hasValue(request.getImageUrl())) throw new AppException(ErrorCode.VALIDATION_ERROR, "Provide either imageUrl or imageStorageKey");
            var asset = catalogAssetService.claimVerifiedAsset(request.getImageStorageKey().trim(), CatalogUploadPurpose.CATEGORY_IMAGE);
            category.setImageUrl(asset.mediaUrl());
            category.setAssetStorageKey(asset.storageKey());
        } else if (!hasValue(request.getImageStorageKey()) && request.getImageUrl() != null) {
            String url = request.getImageUrl().trim();
            category.setImageUrl(url.isEmpty() ? null : url);
            category.setAssetStorageKey(null);
        }
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Category cannot be its own parent");
            }
            Category parent = categoryRepository.findByIdAndDeletedFalse(request.getParentId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            category.setParent(parent);
        }

        category = categoryRepository.save(category);
        if (previousAsset != null && !previousAsset.equals(category.getAssetStorageKey())) catalogAssetService.detachAsset(previousAsset, "CATEGORY", category.getId());
        if (category.getAssetStorageKey() != null && !category.getAssetStorageKey().equals(previousAsset)) catalogAssetService.associateAsset(category.getAssetStorageKey(), CatalogUploadPurpose.CATEGORY_IMAGE, "CATEGORY", category.getId());
        log.info("Category updated: id={}", id);
        auditLogService.log(AuditAction.CATEGORY_UPDATED, "CATEGORY", String.valueOf(id));
        return categoryMapper.toResponse(category);
    }

    @Override
    @CacheEvict(value = AppConstants.CACHE_CATEGORIES, allEntries = true)
    @Transactional
    public void deleteCategory(UUID id) {
        Category category = findOrThrow(id);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        category.softDelete(actor);
        categoryRepository.save(category);
        log.info("Category deleted: id={} by={}", id, actor);
        auditLogService.log(AuditAction.CATEGORY_DELETED, "CATEGORY", String.valueOf(id));
    }

    private Category findOrThrow(UUID id) {
        return categoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private boolean hasValue(String value) { return value != null && !value.isBlank(); }
}
