package com.locnguyen.ecommerce.domains.product.mapper;

import com.locnguyen.ecommerce.domains.product.dto.VariantAttributeResponse;
import com.locnguyen.ecommerce.domains.product.dto.VariantResponse;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttribute;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttributeValue;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ProductVariantMapper {

    public VariantResponse toResponse(ProductVariant variant) {
        return VariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProduct() != null ? variant.getProduct().getId() : null)
                .sku(variant.getSku())
                .barcode(variant.getBarcode())
                .variantName(variant.getVariantName())
                .basePrice(variant.getBasePrice())
                .salePrice(variant.getSalePrice())
                .compareAtPrice(variant.getCompareAtPrice())
                .weightGram(variant.getWeightGram())
                .status(variant.getStatus())
                .attributes(extractAttributes(variant))
                .build();
    }

    public List<VariantResponse> toResponseList(List<ProductVariant> variants) {
        return variants.stream().map(this::toResponse).toList();
    }

    private List<VariantAttributeResponse> extractAttributes(ProductVariant variant) {
        return variant.getAttributeValues().stream()
                .filter(value -> !value.isDeleted())
                .filter(value -> value.getAttribute() == null || !value.getAttribute().isDeleted())
                .map(this::toAttributeResponse)
                .sorted(Comparator
                        .comparing(VariantAttributeResponse::getAttributeName,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(VariantAttributeResponse::getValue,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private VariantAttributeResponse toAttributeResponse(ProductAttributeValue value) {
        ProductAttribute attribute = value.getAttribute();
        return VariantAttributeResponse.builder()
                .attributeId(attribute != null ? attribute.getId() : null)
                .attributeName(attribute != null ? attribute.getName() : null)
                .attributeCode(attribute != null ? attribute.getCode() : null)
                .valueId(value.getId())
                .value(value.getValue())
                .displayValue(value.getDisplayValue())
                .build();
    }
}
