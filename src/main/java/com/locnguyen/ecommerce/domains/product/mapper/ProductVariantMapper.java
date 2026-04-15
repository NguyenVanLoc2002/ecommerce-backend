package com.locnguyen.ecommerce.domains.product.mapper;

import com.locnguyen.ecommerce.domains.product.dto.AttributeResponse;
import com.locnguyen.ecommerce.domains.product.dto.VariantResponse;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProductVariantMapper {

    @Mapping(target = "attributes", source = ".", qualifiedByName = "extractAttributes")
    VariantResponse toResponse(ProductVariant variant);

    List<VariantResponse> toResponseList(List<ProductVariant> variants);

    @Named("extractAttributes")
    default List<AttributeResponse> extractAttributes(ProductVariant variant) {
        return variant.getAttributeValues().stream()
                .map(av -> AttributeResponse.builder()
                        .name(av.getAttribute().getName())
                        .value(av.getValue())
                        .build())
                .sorted(Comparator.comparing(AttributeResponse::getName))
                .collect(Collectors.toList());
    }
}
