package com.locnguyen.ecommerce.domains.productvariant.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.product.dto.CreateVariantRequest;
import com.locnguyen.ecommerce.domains.product.dto.UpdateVariantRequest;
import com.locnguyen.ecommerce.domains.product.dto.VariantResponse;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttribute;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttributeValue;
import com.locnguyen.ecommerce.domains.product.enums.AttributeType;
import com.locnguyen.ecommerce.domains.product.mapper.ProductVariantMapper;
import com.locnguyen.ecommerce.domains.product.repository.ProductAttributeValueRepository;
import com.locnguyen.ecommerce.domains.product.repository.ProductRepository;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.productvariant.enums.ProductVariantStatus;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private static final int MAX_GENERATION_ATTEMPTS = 8;

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final ProductVariantMapper variantMapper;

    // ─── CRUD ────────────────────────────────────────────────────────────────

    @Transactional
    public VariantResponse createVariant(UUID productId, CreateVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        validatePrices(request.getBasePrice(), request.getSalePrice(), request.getCompareAtPrice());
        validateWeight(request.getWeightGram());

        Set<UUID> valueIds = nullSafe(request.getAttributeValueIds());
        Set<ProductAttributeValue> resolved = resolveAndValidateAttributeValues(valueIds);
        ensureNoDuplicateCombination(productId, valueIds, null);

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setBasePrice(request.getBasePrice());
        variant.setSalePrice(request.getSalePrice());
        variant.setCompareAtPrice(request.getCompareAtPrice());
        variant.setWeightGram(request.getWeightGram());
        variant.setStatus(request.getStatus() != null ? request.getStatus() : ProductVariantStatus.ACTIVE);
        variant.setAttributeValues(resolved);

        variant.setSku(resolveSku(request.getSku(), Boolean.TRUE.equals(request.getAutoGenerateSku()),
                product, resolved, null));
        variant.setBarcode(resolveBarcode(request.getBarcode(),
                Boolean.TRUE.equals(request.getAutoGenerateBarcode()), null));
        variant.setVariantName(resolveVariantName(request.getVariantName(),
                Boolean.TRUE.equals(request.getAutoGenerateVariantName()), resolved));

        variant = variantRepository.save(variant);
        product.getVariants().add(variant);
        productRepository.save(product);

        log.info("Variant created: id={} sku={} productId={}",
                variant.getId(), variant.getSku(), productId);
        return variantMapper.toResponse(variant);
    }

    @Transactional
    public VariantResponse updateVariant(UUID productId, UUID variantId, UpdateVariantRequest request) {
        ProductVariant variant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));

        BigDecimal nextBase = request.getBasePrice() != null ? request.getBasePrice() : variant.getBasePrice();
        BigDecimal nextSale = request.getSalePrice() != null ? request.getSalePrice() : variant.getSalePrice();
        BigDecimal nextCompare = request.getCompareAtPrice() != null
                ? request.getCompareAtPrice() : variant.getCompareAtPrice();
        validatePrices(nextBase, nextSale, nextCompare);
        if (request.getWeightGram() != null) {
            validateWeight(request.getWeightGram());
        }

        if (request.getBasePrice() != null) variant.setBasePrice(request.getBasePrice());
        if (request.getSalePrice() != null) variant.setSalePrice(request.getSalePrice());
        if (request.getCompareAtPrice() != null) variant.setCompareAtPrice(request.getCompareAtPrice());
        if (request.getWeightGram() != null) variant.setWeightGram(request.getWeightGram());
        if (request.getStatus() != null) variant.setStatus(request.getStatus());

        Set<ProductAttributeValue> attributeValues = variant.getAttributeValues();
        if (request.getAttributeValueIds() != null) {
            Set<UUID> ids = nullSafe(request.getAttributeValueIds());
            attributeValues = resolveAndValidateAttributeValues(ids);
            ensureNoDuplicateCombination(productId, ids, variantId);
            variant.getAttributeValues().clear();
            variant.getAttributeValues().addAll(attributeValues);
        }

        if (request.getSku() != null || Boolean.TRUE.equals(request.getAutoGenerateSku())) {
            variant.setSku(resolveSku(request.getSku(),
                    Boolean.TRUE.equals(request.getAutoGenerateSku()),
                    variant.getProduct(), attributeValues, variant.getId()));
        }
        if (request.getBarcode() != null || Boolean.TRUE.equals(request.getAutoGenerateBarcode())) {
            variant.setBarcode(resolveBarcode(request.getBarcode(),
                    Boolean.TRUE.equals(request.getAutoGenerateBarcode()), variant.getId()));
        }
        if (request.getVariantName() != null || Boolean.TRUE.equals(request.getAutoGenerateVariantName())) {
            variant.setVariantName(resolveVariantName(request.getVariantName(),
                    Boolean.TRUE.equals(request.getAutoGenerateVariantName()), attributeValues));
        }

        variant = variantRepository.save(variant);
        log.info("Variant updated: id={}", variantId);
        return variantMapper.toResponse(variant);
    }

    @Transactional
    public void deleteVariant(UUID productId, UUID variantId) {
        ProductVariant variant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        variant.softDelete(actor);
        variantRepository.save(variant);
        log.info("Variant deleted: id={} sku={} by={}", variantId, variant.getSku(), actor);
    }

    @Transactional(readOnly = true)
    public List<VariantResponse> getVariantsByProduct(UUID productId) {
        return variantRepository.findByProductIdOrderByCreatedAtAsc(productId)
                .stream().map(variantMapper::toResponse).toList();
    }

    // ─── Validation ─────────────────────────────────────────────────────────

    private void validatePrices(BigDecimal base, BigDecimal sale, BigDecimal compareAt) {
        if (base == null || base.signum() < 0) {
            throw new AppException(ErrorCode.VARIANT_INVALID_PRICE,
                    "Base price is required and must be >= 0");
        }
        if (sale != null && sale.compareTo(base) > 0) {
            throw new AppException(ErrorCode.VARIANT_INVALID_PRICE,
                    "Sale price must be less than or equal to base price");
        }
        if (compareAt != null && compareAt.compareTo(base) < 0) {
            throw new AppException(ErrorCode.VARIANT_INVALID_PRICE,
                    "Compare-at price must be greater than or equal to base price");
        }
    }

    private void validateWeight(Integer weightGram) {
        if (weightGram != null && weightGram <= 0) {
            throw new AppException(ErrorCode.VARIANT_INVALID_WEIGHT);
        }
    }

    private Set<ProductAttributeValue> resolveAndValidateAttributeValues(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return new HashSet<>();
        }
        List<ProductAttributeValue> found = attributeValueRepository.findByIdIn(ids);
        if (found.size() != ids.size()) {
            Set<UUID> foundIds = found.stream().map(ProductAttributeValue::getId)
                    .collect(Collectors.toSet());
            Set<UUID> missing = new HashSet<>(ids);
            missing.removeAll(foundIds);
            throw new AppException(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_NOT_FOUND,
                    "Unknown attribute value ids: " + missing);
        }

        // No DESCRIPTIVE-typed values allowed for variant attributes
        for (ProductAttributeValue value : found) {
            ProductAttribute attribute = value.getAttribute();
            if (attribute == null || attribute.getType() != AttributeType.VARIANT) {
                throw new AppException(ErrorCode.VARIANT_ATTRIBUTE_INVALID,
                        "Only VARIANT-type attribute values are allowed; '"
                                + (attribute != null ? attribute.getCode() : "<unknown>")
                                + "' is not a VARIANT attribute");
            }
        }

        // No two values from the same attribute
        Map<UUID, Long> grouped = found.stream()
                .collect(Collectors.groupingBy(v -> v.getAttribute().getId(), Collectors.counting()));
        grouped.forEach((attrId, count) -> {
            if (count > 1) {
                throw new AppException(ErrorCode.VARIANT_ATTRIBUTE_INVALID,
                        "A variant cannot select more than one value from the same attribute");
            }
        });

        return new HashSet<>(found);
    }

    private void ensureNoDuplicateCombination(UUID productId, Set<UUID> valueIds, UUID excludeVariantId) {
        if (valueIds.isEmpty()) {
            return;
        }
        List<UUID> matches = variantRepository.findVariantIdsWithExactAttributeSet(
                productId, valueIds, valueIds.size());
        boolean conflict = matches.stream()
                .anyMatch(id -> excludeVariantId == null || !id.equals(excludeVariantId));
        if (conflict) {
            throw new AppException(ErrorCode.VARIANT_COMBINATION_DUPLICATE);
        }
    }

    // ─── SKU / Barcode / Name generation ────────────────────────────────────

    private String resolveSku(String requested, boolean autoGenerate, Product product,
                              Collection<ProductAttributeValue> values, UUID excludeVariantId) {
        boolean shouldGenerate = autoGenerate || requested == null || requested.isBlank();
        if (!shouldGenerate) {
            String trimmed = requested.trim();
            if (skuExistsForOther(trimmed, excludeVariantId)) {
                throw new AppException(ErrorCode.SKU_ALREADY_EXISTS);
            }
            return trimmed;
        }
        String base = buildSkuBase(product, values);
        return uniqueByPrefix(base, candidate -> skuExistsForOther(candidate, excludeVariantId));
    }

    private boolean skuExistsForOther(String sku, UUID excludeVariantId) {
        return variantRepository.findBySku(sku)
                .map(existing -> excludeVariantId == null || !existing.getId().equals(excludeVariantId))
                .orElse(false);
    }

    private String resolveBarcode(String requested, boolean autoGenerate, UUID excludeVariantId) {
        boolean blank = requested == null || requested.isBlank();
        if (!autoGenerate && blank) {
            return null;
        }
        if (!autoGenerate) {
            String trimmed = requested.trim();
            if (barcodeExistsForOther(trimmed, excludeVariantId)) {
                throw new AppException(ErrorCode.BARCODE_ALREADY_EXISTS);
            }
            return trimmed;
        }
        return uniqueByPrefix("BC", candidate -> barcodeExistsForOther(candidate, excludeVariantId));
    }

    private boolean barcodeExistsForOther(String barcode, UUID excludeVariantId) {
        if (barcode == null) return false;
        // Quick existence path keeps the call cheap when there's no collision.
        return variantRepository.existsByBarcode(barcode);
    }

    private String resolveVariantName(String requested, boolean autoGenerate,
                                      Collection<ProductAttributeValue> values) {
        boolean shouldGenerate = autoGenerate || requested == null || requested.isBlank();
        if (!shouldGenerate) {
            return requested.trim();
        }
        if (values == null || values.isEmpty()) {
            // No attributes to derive from — fall back to the explicit input or a placeholder.
            return requested != null && !requested.isBlank() ? requested.trim() : "Default";
        }
        return values.stream()
                .sorted(attributeValueOrdering())
                .map(v -> v.getDisplayValue() != null && !v.getDisplayValue().isBlank()
                        ? v.getDisplayValue() : v.getValue())
                .collect(Collectors.joining(" / "));
    }

    private Comparator<ProductAttributeValue> attributeValueOrdering() {
        return Comparator.comparing(
                (ProductAttributeValue v) -> v.getAttribute() != null ? v.getAttribute().getName() : null,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
    }

    /**
     * SKU base: {productSlug-prefix}-{joined-value-codes}. Falls back to a short product code
     * when the slug is empty.
     */
    private String buildSkuBase(Product product, Collection<ProductAttributeValue> values) {
        String slugPart = product != null && product.getSlug() != null && !product.getSlug().isBlank()
                ? slugify(product.getSlug())
                : "PRD";
        if (slugPart.length() > 12) {
            slugPart = slugPart.substring(0, 12);
        }
        if (values == null || values.isEmpty()) {
            return slugPart;
        }
        String tail = values.stream()
                .sorted(attributeValueOrdering())
                .map(v -> slugify(v.getValue()))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("-"));
        return tail.isEmpty() ? slugPart : slugPart + "-" + tail;
    }

    private static String slugify(String input) {
        return input.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private String uniqueByPrefix(String base, Function<String, Boolean> existsCheck) {
        if (!existsCheck.apply(base)) {
            return base;
        }
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String suffix = String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
            String candidate = base + "-" + suffix;
            if (!existsCheck.apply(candidate)) {
                return candidate;
            }
        }
        // Fallback to UUID-based suffix; collisions here are astronomically unlikely.
        return base + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static <T> Set<T> nullSafe(Set<T> input) {
        return input == null ? new LinkedHashSet<>() : new LinkedHashSet<>(input);
    }
}
