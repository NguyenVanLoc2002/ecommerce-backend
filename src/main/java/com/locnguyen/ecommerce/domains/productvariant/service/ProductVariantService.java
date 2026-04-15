package com.locnguyen.ecommerce.domains.productvariant.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.product.dto.AttributeRequest;
import com.locnguyen.ecommerce.domains.product.dto.CreateVariantRequest;
import com.locnguyen.ecommerce.domains.product.dto.UpdateVariantRequest;
import com.locnguyen.ecommerce.domains.product.dto.VariantResponse;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttributeValue;
import com.locnguyen.ecommerce.domains.product.mapper.ProductVariantMapper;
import com.locnguyen.ecommerce.domains.product.repository.ProductAttributeRepository;
import com.locnguyen.ecommerce.domains.product.repository.ProductAttributeValueRepository;
import com.locnguyen.ecommerce.domains.product.repository.ProductRepository;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.productvariant.enums.ProductVariantStatus;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final ProductAttributeRepository attributeRepository;
    private final ProductVariantMapper variantMapper;

    @Transactional
    public VariantResponse createVariant(Long productId, CreateVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (variantRepository.existsBySku(request.getSku())) {
            throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(request.getSku().trim());
        variant.setBarcode(request.getBarcode());
        variant.setVariantName(request.getVariantName().trim());
        variant.setBasePrice(request.getBasePrice());
        variant.setSalePrice(request.getSalePrice());
        variant.setCompareAtPrice(request.getCompareAtPrice());
        variant.setWeightGram(request.getWeightGram());
        variant.setStatus(request.getStatus() != null ? request.getStatus() : ProductVariantStatus.ACTIVE);

        // Resolve and attach attribute values
        if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
            Set<ProductAttributeValue> values = resolveAttributeValues(request.getAttributes());
            variant.setAttributeValues(values);
        }

        variant = variantRepository.save(variant);
        product.getVariants().add(variant);
        productRepository.save(product);

        log.info("Variant created: id={} sku={} productId={}", variant.getId(), variant.getSku(), productId);
        return variantMapper.toResponse(variant);
    }

    @Transactional
    public VariantResponse updateVariant(Long productId, Long variantId, UpdateVariantRequest request) {
        ProductVariant variant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));

        if (request.getSku() != null) {
            String newSku = request.getSku().trim();
            if (!newSku.equals(variant.getSku()) && variantRepository.existsBySku(newSku)) {
                throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
            }
            variant.setSku(newSku);
        }
        if (request.getBarcode() != null) variant.setBarcode(request.getBarcode());
        if (request.getVariantName() != null) variant.setVariantName(request.getVariantName().trim());
        if (request.getBasePrice() != null) variant.setBasePrice(request.getBasePrice());
        if (request.getSalePrice() != null) variant.setSalePrice(request.getSalePrice());
        if (request.getCompareAtPrice() != null) variant.setCompareAtPrice(request.getCompareAtPrice());
        if (request.getWeightGram() != null) variant.setWeightGram(request.getWeightGram());
        if (request.getStatus() != null) variant.setStatus(request.getStatus());

        // Replace attributes if provided
        if (request.getAttributes() != null) {
            variant.getAttributeValues().clear();
            Set<ProductAttributeValue> values = resolveAttributeValues(request.getAttributes());
            variant.setAttributeValues(values);
        }

        variant = variantRepository.save(variant);
        log.info("Variant updated: id={}", variantId);
        return variantMapper.toResponse(variant);
    }

    @Transactional
    public void deleteVariant(Long productId, Long variantId) {
        ProductVariant variant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
        String actor = com.locnguyen.ecommerce.common.utils.SecurityUtils.getCurrentUsernameOrSystem();
        variant.softDelete(actor);
        variantRepository.save(variant);
        log.info("Variant deleted: id={} sku={} by={}", variantId, variant.getSku(), actor);
    }

    @Transactional(readOnly = true)
    public List<VariantResponse> getVariantsByProduct(Long productId) {
        return variantRepository.findByProductIdOrderByCreatedAtAsc(productId)
                .stream().map(variantMapper::toResponse).toList();
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    /**
     * Resolves attribute requests to {@link ProductAttributeValue} entities.
     * Uses "find or create" semantics so the same attribute+value is shared across variants.
     */
    private Set<ProductAttributeValue> resolveAttributeValues(List<AttributeRequest> requests) {
        Set<ProductAttributeValue> values = new HashSet<>();
        for (AttributeRequest ar : requests) {
            String attrName = ar.getAttributeName().trim();
            String valueStr = ar.getValue().trim();
            ProductAttributeValue value = attributeValueRepository
                    .findByAttributeCodeAndValue(attrName, valueStr)
                    .orElseGet(() -> {
                        com.locnguyen.ecommerce.domains.product.entity.ProductAttribute attr =
                                attributeRepository.findByCode(attrName)
                                        .orElseGet(() -> {
                                            com.locnguyen.ecommerce.domains.product.entity.ProductAttribute newAttr =
                                                    new com.locnguyen.ecommerce.domains.product.entity.ProductAttribute();
                                            newAttr.setName(attrName);
                                            newAttr.setCode(attrName.toLowerCase().replace(" ", "_"));
                                            return attributeRepository.save(newAttr);
                                        });
                        com.locnguyen.ecommerce.domains.product.entity.ProductAttributeValue newValue =
                                new com.locnguyen.ecommerce.domains.product.entity.ProductAttributeValue();
                        newValue.setAttribute(attr);
                        newValue.setValue(valueStr);
                        newValue.setDisplayValue(valueStr);
                        return attributeValueRepository.save(newValue);
                    });
            values.add(value);
        }
        return values;
    }
}
