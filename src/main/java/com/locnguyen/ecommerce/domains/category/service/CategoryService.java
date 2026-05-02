package com.locnguyen.ecommerce.domains.category.service;

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
import com.locnguyen.ecommerce.domains.category.specification.CategorySpecification;
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
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final AuditLogService auditLogService;

    // ─── Public ───────────────────────────────────────────────────────────────

    /**
     * Get paginated categories with optional filters.
     * Not cached since it's only used in admin panel and can have many combinations.
     */
    public PagedResponse<CategoryResponse> getAllCategories(
            CategoryFilter filter,
            Pageable pageable
    ) {
        Page<Category> pageCategories = categoryRepository.findAll(
                CategorySpecification.withFilter(filter), pageable
        );
        return PagedResponse.of(pageCategories.map(categoryMapper::toResponse));
    }

    /**
     * List all active categories ordered by sort order.
     * Cached for 30 minutes — evicted whenever any category is mutated.
     */
    @Cacheable(value = AppConstants.CACHE_CATEGORIES, key = "'active'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByStatusOrderBySortOrderAsc(CategoryStatus.ACTIVE)
                .stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Get category by ID.
     * Cached by ID for 30 minutes.
     */
    @Cacheable(value = AppConstants.CACHE_CATEGORIES, key = "'id:' + #id")
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        return categoryMapper.toResponse(findOrThrow(id));
    }

    // ─── Admin CRUD ──────────────────────────────────────────────────────────

    @CacheEvict(value = AppConstants.CACHE_CATEGORIES, allEntries = true)
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
        }

        Category category = new Category();
        category.setName(request.getName().trim());
        category.setSlug(request.getSlug().trim());
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        category.setStatus(CategoryStatus.ACTIVE);
        category.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        if (request.getParentId() != null) {
            Category parent = categoryRepository.findByIdAndDeletedFalse(request.getParentId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            category.setParent(parent);
        }

        category = categoryRepository.save(category);
        log.info("Category created: id={} name={}", category.getId(), category.getName());
        auditLogService.log(AuditAction.CATEGORY_CREATED, "CATEGORY",
                String.valueOf(category.getId()), "name=" + category.getName());
        return categoryMapper.toResponse(category);
    }

    @CacheEvict(value = AppConstants.CACHE_CATEGORIES, allEntries = true)
    @Transactional
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        Category category = findOrThrow(id);

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
        if (request.getImageUrl() != null) {
            category.setImageUrl(request.getImageUrl());
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
        log.info("Category updated: id={}", id);
        auditLogService.log(AuditAction.CATEGORY_UPDATED, "CATEGORY", String.valueOf(id));
        return categoryMapper.toResponse(category);
    }

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

    // ─── Internal ────────────────────────────────────────────────────────────

    private Category findOrThrow(UUID id) {
        return categoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}
