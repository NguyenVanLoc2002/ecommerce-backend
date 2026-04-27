package com.locnguyen.ecommerce.domains.brand.service;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.brand.dto.BrandFilter;
import com.locnguyen.ecommerce.domains.brand.dto.BrandResponse;
import com.locnguyen.ecommerce.domains.brand.dto.CreateBrandRequest;
import com.locnguyen.ecommerce.domains.brand.dto.UpdateBrandRequest;
import com.locnguyen.ecommerce.domains.brand.entity.Brand;
import com.locnguyen.ecommerce.domains.brand.enums.BrandStatus;
import com.locnguyen.ecommerce.domains.brand.mapper.BrandMapper;
import com.locnguyen.ecommerce.domains.brand.repository.BrandRepository;
import com.locnguyen.ecommerce.domains.brand.specification.BrandSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandMapper brandMapper;
    private final AuditLogService auditLogService;

    // ─── Admin operations ─────────────────────────────────────────────────────

    /** Paginated, filterable brand list for admin — bypasses cache. */
    @Transactional(readOnly = true)
    public PagedResponse<BrandResponse> getBrands(BrandFilter filter, Pageable pageable) {
        return PagedResponse.of(
                brandRepository.findAll(BrandSpecification.withFilter(filter), pageable)
                        .map(brandMapper::toResponse)
        );
    }

    // ─── Public / shared operations ───────────────────────────────────────────

    /**
     * List all active brands.
     * Cached for 30 minutes — evicted when any brand mutates.
     */
    @Cacheable(value = AppConstants.CACHE_BRANDS, key = "'active'")
    @Transactional(readOnly = true)
    public List<BrandResponse> getActiveBrands() {
        return brandRepository.findByStatusOrderBySortOrderAsc(BrandStatus.ACTIVE)
                .stream().map(brandMapper::toResponse).toList();
    }

    /**
     * Get brand by ID.
     * Cached by ID for 30 minutes.
     */
    @Cacheable(value = AppConstants.CACHE_BRANDS, key = "'id:' + #id")
    @Transactional(readOnly = true)
    public BrandResponse getBrandById(Long id) {
        return brandMapper.toResponse(findOrThrow(id));
    }

    @CacheEvict(value = AppConstants.CACHE_BRANDS, allEntries = true)
    @Transactional
    public BrandResponse createBrand(CreateBrandRequest request) {
        if (brandRepository.existsBySlug(request.getSlug())) {
            throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        Brand brand = new Brand();
        brand.setName(request.getName().trim());
        brand.setSlug(request.getSlug().trim());
        brand.setLogoUrl(request.getLogoUrl());
        brand.setDescription(request.getDescription());
        brand.setStatus(BrandStatus.ACTIVE);
        brand = brandRepository.save(brand);
        log.info("Brand created: id={} name={}", brand.getId(), brand.getName());
        auditLogService.log(AuditAction.BRAND_CREATED, "BRAND",
                String.valueOf(brand.getId()), "name=" + brand.getName());
        return brandMapper.toResponse(brand);
    }

    @CacheEvict(value = AppConstants.CACHE_BRANDS, allEntries = true)
    @Transactional
    public BrandResponse updateBrand(Long id, UpdateBrandRequest request) {
        Brand brand = findOrThrow(id);
        if (request.getName() != null) brand.setName(request.getName().trim());
        if (request.getSlug() != null) {
            String newSlug = request.getSlug().trim();
            if (!newSlug.equals(brand.getSlug()) && brandRepository.existsBySlug(newSlug)) {
                throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
            }
            brand.setSlug(newSlug);
        }
        if (request.getLogoUrl() != null) brand.setLogoUrl(request.getLogoUrl());
        if (request.getDescription() != null) brand.setDescription(request.getDescription());
        if (request.getStatus() != null) brand.setStatus(request.getStatus());
        brand = brandRepository.save(brand);
        log.info("Brand updated: id={}", id);
        auditLogService.log(AuditAction.BRAND_UPDATED, "BRAND", String.valueOf(id));
        return brandMapper.toResponse(brand);
    }

    @CacheEvict(value = AppConstants.CACHE_BRANDS, allEntries = true)
    @Transactional
    public void deleteBrand(Long id) {
        Brand brand = findOrThrow(id);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        brand.softDelete(actor);
        brandRepository.save(brand);
        log.info("Brand deleted: id={} by={}", id, actor);
        auditLogService.log(AuditAction.BRAND_DELETED, "BRAND", String.valueOf(id));
    }

    private Brand findOrThrow(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
    }
}
