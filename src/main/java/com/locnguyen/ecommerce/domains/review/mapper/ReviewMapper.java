package com.locnguyen.ecommerce.domains.review.mapper;

import com.locnguyen.ecommerce.domains.review.dto.ReviewResponse;
import com.locnguyen.ecommerce.domains.review.entity.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(target = "customerId",   source = "customer.id")
    @Mapping(target = "customerName", expression = "java(review.getCustomer().getUser().getFirstName() + \" \" + review.getCustomer().getUser().getLastName())")
    @Mapping(target = "productId",    source = "product.id")
    @Mapping(target = "productName",  source = "product.name")
    @Mapping(target = "variantId",    source = "variant.id")
    @Mapping(target = "variantName",  source = "variant.variantName")
    @Mapping(target = "sku",          source = "variant.sku")
    @Mapping(target = "orderItemId",  source = "orderItem.id")
    @Mapping(target = "status",       expression = "java(review.getStatus().name())")
    ReviewResponse toResponse(Review review);
}
