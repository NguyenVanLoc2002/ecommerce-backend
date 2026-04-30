package com.locnguyen.ecommerce.domains.product.mapper;

import com.locnguyen.ecommerce.domains.product.dto.attribute.ProductAttributeResponse;
import com.locnguyen.ecommerce.domains.product.dto.attribute.ProductAttributeValueResponse;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttribute;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttributeValue;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ProductAttributeMapper {

    public ProductAttributeResponse toResponse(ProductAttribute attribute) {
        List<ProductAttributeValueResponse> values = attribute.getValues().stream()
                .filter(v -> !v.isDeleted())
                .sorted(Comparator.comparing(ProductAttributeValue::getValue,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toValueResponse)
                .toList();

        return ProductAttributeResponse.builder()
                .id(attribute.getId())
                .name(attribute.getName())
                .code(attribute.getCode())
                .type(attribute.getType())
                .values(values)
                .createdAt(attribute.getCreatedAt())
                .updatedAt(attribute.getUpdatedAt())
                .build();
    }

    public ProductAttributeValueResponse toValueResponse(ProductAttributeValue value) {
        return ProductAttributeValueResponse.builder()
                .id(value.getId())
                .value(value.getValue())
                .displayValue(value.getDisplayValue())
                .build();
    }
}
