package com.locnguyen.ecommerce.domains.product.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.product.dto.attribute.CreateProductAttributeRequest;
import com.locnguyen.ecommerce.domains.product.dto.attribute.CreateProductAttributeValueRequest;
import com.locnguyen.ecommerce.domains.product.dto.attribute.ProductAttributeFilter;
import com.locnguyen.ecommerce.domains.product.dto.attribute.ProductAttributeResponse;
import com.locnguyen.ecommerce.domains.product.dto.attribute.ProductAttributeValueResponse;
import com.locnguyen.ecommerce.domains.product.dto.attribute.UpdateProductAttributeRequest;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttribute;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttributeValue;
import com.locnguyen.ecommerce.domains.product.mapper.ProductAttributeMapper;
import com.locnguyen.ecommerce.domains.product.repository.ProductAttributeRepository;
import com.locnguyen.ecommerce.domains.product.repository.ProductAttributeValueRepository;
import com.locnguyen.ecommerce.domains.product.specification.ProductAttributeSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Admin service managing reusable product attributes ({@code Color}, {@code Size}, …)
 * and their values. Variant attribute selection is built from the {@code VARIANT}
 * type rows produced here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAttributeService {

    private final ProductAttributeRepository attributeRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductAttributeMapper attributeMapper;

    // ─── Attribute CRUD ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<ProductAttributeResponse> getAttributes(ProductAttributeFilter filter, Pageable pageable) {
        return PagedResponse.of(
                attributeRepository.findAll(ProductAttributeSpecification.withFilter(filter), pageable)
                        .map(attributeMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public ProductAttributeResponse getAttribute(UUID id) {
        return attributeMapper.toResponse(findAttributeOrThrow(id));
    }

    @Transactional
    public ProductAttributeResponse createAttribute(CreateProductAttributeRequest request) {
        String code = AttributeCodeNormalizer.normalize(request.getCode());
        if (code == null || code.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Attribute code is required");
        }
        if (attributeRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.PRODUCT_ATTRIBUTE_CODE_ALREADY_EXISTS);
        }

        ProductAttribute attribute = new ProductAttribute();
        attribute.setName(request.getName().trim());
        attribute.setCode(code);
        attribute.setType(request.getType());

        if (request.getValues() != null) {
            Set<String> seen = new HashSet<>();
            for (CreateProductAttributeValueRequest valueRequest : request.getValues()) {
                String value = valueRequest.getValue().trim();
                if (!seen.add(value.toUpperCase())) {
                    throw new AppException(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_ALREADY_EXISTS,
                            "Duplicate value in request: " + value);
                }
                ProductAttributeValue child = new ProductAttributeValue();
                child.setAttribute(attribute);
                child.setValue(value);
                child.setDisplayValue(trimToNull(valueRequest.getDisplayValue()));
                attribute.getValues().add(child);
            }
        }

        attribute = attributeRepository.save(attribute);
        log.info("Product attribute created: id={} code={} type={}",
                attribute.getId(), attribute.getCode(), attribute.getType());
        return attributeMapper.toResponse(attribute);
    }

    @Transactional
    public ProductAttributeResponse updateAttribute(UUID id, UpdateProductAttributeRequest request) {
        ProductAttribute attribute = findAttributeOrThrow(id);

        if (request.getName() != null) {
            attribute.setName(request.getName().trim());
        }
        if (request.getCode() != null) {
            String code = AttributeCodeNormalizer.normalize(request.getCode());
            if (code == null || code.isBlank()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Attribute code is required");
            }
            if (!code.equals(attribute.getCode())
                    && attributeRepository.existsByCodeAndIdNot(code, id)) {
                throw new AppException(ErrorCode.PRODUCT_ATTRIBUTE_CODE_ALREADY_EXISTS);
            }
            attribute.setCode(code);
        }
        if (request.getType() != null) {
            attribute.setType(request.getType());
        }

        if (request.getValues() != null) {
            replaceValues(attribute, request.getValues());
        }

        attribute = attributeRepository.save(attribute);
        log.info("Product attribute updated: id={}", id);
        return attributeMapper.toResponse(attribute);
    }

    @Transactional
    public void deleteAttribute(UUID id) {
        ProductAttribute attribute = findAttributeOrThrow(id);
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        // Soft-delete the attribute and all of its remaining values. Values that
        // are still referenced by variants stay readable through the join because
        // variant_attribute_values rows are not touched — variant snapshots remain
        // intact and existing orders are unaffected.
        for (ProductAttributeValue value : attribute.getValues()) {
            if (!value.isDeleted()) {
                value.softDelete(actor);
            }
        }
        attribute.softDelete(actor);
        attributeRepository.save(attribute);
        log.info("Product attribute deleted: id={} by={}", id, actor);
    }

    // ─── Value CRUD ─────────────────────────────────────────────────────────

    @Transactional
    public ProductAttributeValueResponse addValue(UUID attributeId,
                                                  CreateProductAttributeValueRequest request) {
        ProductAttribute attribute = findAttributeOrThrow(attributeId);
        String value = request.getValue().trim();

        if (valueRepository.existsByAttributeIdAndValue(attributeId, value)) {
            throw new AppException(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_ALREADY_EXISTS);
        }

        ProductAttributeValue created = new ProductAttributeValue();
        created.setAttribute(attribute);
        created.setValue(value);
        created.setDisplayValue(trimToNull(request.getDisplayValue()));
        created = valueRepository.save(created);

        log.info("Attribute value added: id={} attributeId={} value={}",
                created.getId(), attributeId, value);
        return attributeMapper.toValueResponse(created);
    }

    @Transactional
    public ProductAttributeValueResponse updateValue(UUID attributeId, UUID valueId,
                                                     CreateProductAttributeValueRequest request) {
        ProductAttributeValue value = findValueOrThrow(attributeId, valueId);
        String newValue = request.getValue().trim();

        if (!newValue.equals(value.getValue())
                && valueRepository.existsByAttributeIdAndValueAndIdNot(attributeId, newValue, valueId)) {
            throw new AppException(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_ALREADY_EXISTS);
        }
        value.setValue(newValue);
        value.setDisplayValue(trimToNull(request.getDisplayValue()));
        value = valueRepository.save(value);

        log.info("Attribute value updated: id={}", valueId);
        return attributeMapper.toValueResponse(value);
    }

    @Transactional
    public void deleteValue(UUID attributeId, UUID valueId) {
        ProductAttributeValue value = findValueOrThrow(attributeId, valueId);
        if (valueRepository.isUsedByAnyVariant(valueId)) {
            throw new AppException(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_IN_USE);
        }
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        value.softDelete(actor);
        valueRepository.save(value);
        log.info("Attribute value deleted: id={} by={}", valueId, actor);
    }

    // ─── Internal ───────────────────────────────────────────────────────────

    private ProductAttribute findAttributeOrThrow(UUID id) {
        return attributeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_ATTRIBUTE_NOT_FOUND));
    }

    private ProductAttributeValue findValueOrThrow(UUID attributeId, UUID valueId) {
        ProductAttributeValue value = valueRepository.findById(valueId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_NOT_FOUND));
        if (value.getAttribute() == null || !attributeId.equals(value.getAttribute().getId())) {
            throw new AppException(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_NOT_FOUND);
        }
        return value;
    }

    /**
     * Replaces the value set under {@code attribute} with the requested list.
     * - existing values whose {@code value} matches a request item are kept and updated
     * - missing existing values are removed (soft-deleted) — but only if not referenced
     *   by any variant; otherwise the request is rejected so we never break a variant
     * - new request items are added
     */
    private void replaceValues(ProductAttribute attribute,
                               List<CreateProductAttributeValueRequest> requests) {
        Set<String> seen = new HashSet<>();
        Map<String, CreateProductAttributeValueRequest> wanted = new HashMap<>();
        for (CreateProductAttributeValueRequest r : requests) {
            String key = r.getValue().trim();
            if (!seen.add(key.toUpperCase())) {
                throw new AppException(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_ALREADY_EXISTS,
                        "Duplicate value in request: " + key);
            }
            wanted.put(key, r);
        }

        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        // Update existing / mark removed
        for (ProductAttributeValue existing : List.copyOf(attribute.getValues())) {
            if (existing.isDeleted()) continue;
            CreateProductAttributeValueRequest match = wanted.remove(existing.getValue());
            if (match != null) {
                existing.setDisplayValue(trimToNull(match.getDisplayValue()));
            } else {
                if (valueRepository.isUsedByAnyVariant(existing.getId())) {
                    throw new AppException(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_IN_USE,
                            "Cannot remove value '" + existing.getValue() + "' — it is in use");
                }
                existing.softDelete(actor);
            }
        }

        // Add new
        for (CreateProductAttributeValueRequest r : wanted.values()) {
            ProductAttributeValue child = new ProductAttributeValue();
            child.setAttribute(attribute);
            child.setValue(r.getValue().trim());
            child.setDisplayValue(trimToNull(r.getDisplayValue()));
            attribute.getValues().add(child);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
