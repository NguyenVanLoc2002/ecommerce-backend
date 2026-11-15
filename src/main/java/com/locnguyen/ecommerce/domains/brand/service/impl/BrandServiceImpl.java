package com.locnguyen.ecommerce.domains.brand.service.impl;

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
import com.locnguyen.ecommerce.domains.brand.service.BrandService;
import com.locnguyen.ecommerce.domains.brand.specification.BrandSpecification;
import com.locnguyen.ecommerce.domains.catalogasset.enums.CatalogUploadPurpose;
import com.locnguyen.ecommerce.domains.catalogasset.service.CatalogAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;
    private final BrandMapper brandMapper;
    private final AuditLogService auditLogService;
    private final CatalogAssetService catalogAssetService;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<BrandResponse> getBrands(BrandFilter filter, Pageable pageable) {
        return PagedResponse.of(
                brandRepository.findAll(BrandSpecification.withFilter(filter), pageable)
                        .map(brandMapper::toResponse)
        );
    }

    @Override
    @Cacheable(value = AppConstants.CACHE_BRANDS, key = "'active'")
    @Transactional(readOnly = true)
    public List<BrandResponse> getActiveBrands() {
        return brandRepository.findByStatusOrderBySortOrderAsc(BrandStatus.ACTIVE)
                .stream().map(brandMapper::toResponse).toList();
    }

    @Override
    @Cacheable(value = AppConstants.CACHE_BRANDS, key = "'id:' + #id")
    @Transactional(readOnly = true)
    public BrandResponse getBrandById(UUID id) {
        return brandMapper.toResponse(findOrThrow(id));
    }

    @Override
    @CacheEvict(value = AppConstants.CACHE_BRANDS, allEntries = true)
    @Transactional
    public BrandResponse createBrand(CreateBrandRequest request) {
        if (brandRepository.existsBySlug(request.getSlug())) {
            throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        Brand brand = new Brand();
        brand.setName(request.getName().trim());
        brand.setSlug(request.getSlug().trim());
        if (hasValue(request.getLogoStorageKey()) && hasValue(request.getLogoUrl())) throw new AppException(ErrorCode.VALIDATION_ERROR, "Provide either logoUrl or logoStorageKey");
        if (hasValue(request.getLogoStorageKey())) {
            var asset = catalogAssetService.claimVerifiedAsset(request.getLogoStorageKey().trim(), CatalogUploadPurpose.BRAND_LOGO);
            brand.setLogoUrl(asset.mediaUrl());
            brand.setAssetStorageKey(asset.storageKey());
        } else brand.setLogoUrl(request.getLogoUrl());
        brand.setDescription(request.getDescription());
        brand.setStatus(BrandStatus.ACTIVE);
        brand = brandRepository.save(brand);
        if (brand.getAssetStorageKey() != null) catalogAssetService.associateAsset(brand.getAssetStorageKey(), CatalogUploadPurpose.BRAND_LOGO, "BRAND", brand.getId());
        log.info("Brand created: id={} name={}", brand.getId(), brand.getName());
        auditLogService.log(AuditAction.BRAND_CREATED, "BRAND",
                String.valueOf(brand.getId()), "name=" + brand.getName());
        return brandMapper.toResponse(brand);
    }

    @Override
    @CacheEvict(value = AppConstants.CACHE_BRANDS, allEntries = true)
    @Transactional
    public BrandResponse updateBrand(UUID id, UpdateBrandRequest request) {
        Brand brand = findOrThrow(id);
        if (request.getName() != null) brand.setName(request.getName().trim());
        if (request.getSlug() != null) {
            String newSlug = request.getSlug().trim();
            if (!newSlug.equals(brand.getSlug()) && brandRepository.existsBySlug(newSlug)) {
                throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
            }
            brand.setSlug(newSlug);
        }
        String previousAsset = brand.getAssetStorageKey();
        boolean sameAsset = hasValue(request.getLogoStorageKey()) && request.getLogoStorageKey().trim().equals(previousAsset);
        if (hasValue(request.getLogoStorageKey()) && !sameAsset) {
            if (hasValue(request.getLogoUrl())) throw new AppException(ErrorCode.VALIDATION_ERROR, "Provide either logoUrl or logoStorageKey");
            var asset = catalogAssetService.claimVerifiedAsset(request.getLogoStorageKey().trim(), CatalogUploadPurpose.BRAND_LOGO);
            brand.setLogoUrl(asset.mediaUrl());
            brand.setAssetStorageKey(asset.storageKey());
        } else if (!hasValue(request.getLogoStorageKey()) && request.getLogoUrl() != null) {
            String url = request.getLogoUrl().trim();
            brand.setLogoUrl(url.isEmpty() ? null : url);
            brand.setAssetStorageKey(null);
        }
        if (request.getDescription() != null) brand.setDescription(request.getDescription());
        if (request.getStatus() != null) brand.setStatus(request.getStatus());
        brand = brandRepository.save(brand);
        if (previousAsset != null && !previousAsset.equals(brand.getAssetStorageKey())) catalogAssetService.detachAsset(previousAsset, "BRAND", brand.getId());
        if (brand.getAssetStorageKey() != null && !brand.getAssetStorageKey().equals(previousAsset)) catalogAssetService.associateAsset(brand.getAssetStorageKey(), CatalogUploadPurpose.BRAND_LOGO, "BRAND", brand.getId());
        log.info("Brand updated: id={}", id);
        auditLogService.log(AuditAction.BRAND_UPDATED, "BRAND", String.valueOf(id));
        return brandMapper.toResponse(brand);
    }

    @Override
    @CacheEvict(value = AppConstants.CACHE_BRANDS, allEntries = true)
    @Transactional
    public void deleteBrand(UUID id) {
        Brand brand = findOrThrow(id);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        brand.softDelete(actor);
        brandRepository.save(brand);
        log.info("Brand deleted: id={} by={}", id, actor);
        auditLogService.log(AuditAction.BRAND_DELETED, "BRAND", String.valueOf(id));
    }

    private Brand findOrThrow(UUID id) {
        return brandRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));
    }

    private boolean hasValue(String value) { return value != null && !value.isBlank(); }
}
