package com.locnguyen.ecommerce.domains.product.service.impl;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.product.dto.CreateProductMediaRequest;
import com.locnguyen.ecommerce.domains.product.dto.MediaResponse;
import com.locnguyen.ecommerce.domains.product.dto.UpdateProductMediaRequest;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.product.entity.ProductMedia;
import com.locnguyen.ecommerce.domains.product.enums.MediaType;
import com.locnguyen.ecommerce.domains.product.repository.ProductMediaRepository;
import com.locnguyen.ecommerce.domains.product.repository.ProductRepository;
import com.locnguyen.ecommerce.domains.product.service.ProductMediaService;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMediaServiceImpl implements ProductMediaService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductMediaRepository mediaRepository;
    private final AuditLogService auditLogService;

    @Override
    @Caching(evict = {
            @CacheEvict(value = AppConstants.CACHE_PRODUCT_DETAIL, key = "#productId"),
            @CacheEvict(value = AppConstants.CACHE_PRODUCTS, allEntries = true)
    })
    @Transactional
    public MediaResponse createMedia(UUID productId, CreateProductMediaRequest request) {
        Product product = findProductOrThrow(productId);
        MediaType mediaType = request.getMediaType() != null ? request.getMediaType() : MediaType.IMAGE;
        if (request.isPrimary() && mediaType != MediaType.IMAGE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Only image media can be primary");
        }

        ProductMedia media = new ProductMedia();
        media.setProduct(product);
        media.setVariant(resolveVariant(productId, request.getVariantId()));
        media.setMediaUrl(request.getMediaUrl().trim());
        media.setMediaType(mediaType);
        media.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        if (request.isPrimary()) {
            clearPrimary(productId);
            media.setPrimary(true);
        }

        media = mediaRepository.save(media);
        auditLogService.log(AuditAction.PRODUCT_UPDATED, "PRODUCT_MEDIA", String.valueOf(media.getId()),
                "productId=" + productId + " action=created");
        log.info("Product media created: id={} productId={}", media.getId(), productId);
        return toResponse(media);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = AppConstants.CACHE_PRODUCT_DETAIL, key = "#productId"),
            @CacheEvict(value = AppConstants.CACHE_PRODUCTS, allEntries = true)
    })
    @Transactional
    public MediaResponse updateMedia(UUID productId, UUID mediaId, UpdateProductMediaRequest request) {
        findProductOrThrow(productId);
        ProductMedia media = findMediaOrThrow(productId, mediaId);
        MediaType mediaType = request.getMediaType() != null ? request.getMediaType() : media.getMediaType();
        boolean primary = request.getPrimary() != null ? request.getPrimary() : media.isPrimary();
        if (primary && mediaType != MediaType.IMAGE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Only image media can be primary");
        }

        if (request.getMediaUrl() != null) media.setMediaUrl(request.getMediaUrl().trim());
        if (request.getMediaType() != null) media.setMediaType(mediaType);
        if (request.getSortOrder() != null) media.setSortOrder(request.getSortOrder());
        if (request.isVariantIdProvided()) media.setVariant(resolveVariant(productId, request.getVariantId()));
        if (primary) clearPrimary(productId);
        media.setPrimary(primary);

        media = mediaRepository.save(media);
        auditLogService.log(AuditAction.PRODUCT_UPDATED, "PRODUCT_MEDIA", String.valueOf(mediaId),
                "productId=" + productId + " action=updated");
        log.info("Product media updated: id={} productId={}", mediaId, productId);
        return toResponse(media);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = AppConstants.CACHE_PRODUCT_DETAIL, key = "#productId"),
            @CacheEvict(value = AppConstants.CACHE_PRODUCTS, allEntries = true)
    })
    @Transactional
    public void deleteMedia(UUID productId, UUID mediaId) {
        findProductOrThrow(productId);
        ProductMedia media = findMediaOrThrow(productId, mediaId);
        mediaRepository.delete(media);
        auditLogService.log(AuditAction.PRODUCT_UPDATED, "PRODUCT_MEDIA", String.valueOf(mediaId),
                "productId=" + productId + " action=deleted");
        log.info("Product media deleted: id={} productId={}", mediaId, productId);
    }

    private Product findProductOrThrow(UUID productId) {
        return productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private ProductMedia findMediaOrThrow(UUID productId, UUID mediaId) {
        return mediaRepository.findByIdAndProductId(mediaId, productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_MEDIA_NOT_FOUND));
    }

    private ProductVariant resolveVariant(UUID productId, UUID variantId) {
        if (variantId == null) return null;
        return variantRepository.findByIdAndProductIdAndDeletedFalse(variantId, productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
    }

    private void clearPrimary(UUID productId) {
        mediaRepository.findByProductIdAndPrimaryTrue(productId)
                .forEach(existing -> existing.setPrimary(false));
    }

    private MediaResponse toResponse(ProductMedia media) {
        return MediaResponse.builder()
                .id(media.getId())
                .mediaUrl(media.getMediaUrl())
                .mediaType(media.getMediaType().name())
                .sortOrder(media.getSortOrder())
                .primary(media.isPrimary())
                .variantId(media.getVariant() != null ? media.getVariant().getId() : null)
                .build();
    }
}
