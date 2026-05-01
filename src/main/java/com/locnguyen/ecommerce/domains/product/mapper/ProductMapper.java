package com.locnguyen.ecommerce.domains.product.mapper;

import com.locnguyen.ecommerce.domains.brand.mapper.BrandMapper;
import com.locnguyen.ecommerce.domains.category.mapper.CategoryMapper;
import com.locnguyen.ecommerce.domains.product.dto.ProductDetailResponse;
import com.locnguyen.ecommerce.domains.product.dto.ProductListItemResponse;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * Maps Product entity to list-item and detail DTOs.
 * Does NOT expose entity-level relationships directly.
 */
@Mapper(componentModel = "spring",
        uses = {BrandMapper.class, CategoryMapper.class, ProductVariantMapper.class})
public interface ProductMapper {

    @Mapping(target = "thumbnailUrl", source = ".", qualifiedByName = "extractThumbnail")
    @Mapping(target = "minPrice", source = ".", qualifiedByName = "extractMinPrice")
    @Mapping(target = "maxPrice", source = ".", qualifiedByName = "extractMaxPrice")
    @Mapping(target = "brandName", source = ".", qualifiedByName = "extractBrandName")
    @Mapping(target = "categoryNames", source = ".", qualifiedByName = "extractCategoryNames")
    ProductListItemResponse toListItem(Product product);

    ProductDetailResponse toDetail(Product product);

    // ─── Helper methods ──────────────────────────────────────────────────────

    @org.mapstruct.Named("extractThumbnail")
    default String extractThumbnail(Product product) {
        return product.getMedia().stream()
                .filter(m -> m.isPrimary())
                .map(com.locnguyen.ecommerce.domains.product.entity.ProductMedia::getMediaUrl)
                .findFirst()
                .orElse(null);
    }

    @org.mapstruct.Named("extractMinPrice")
    default BigDecimal extractMinPrice(Product product) {
        return product.getVariants().stream()
                .filter(variant -> !variant.isDeleted())
                .map(this::effectivePrice)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    @org.mapstruct.Named("extractMaxPrice")
    default BigDecimal extractMaxPrice(Product product) {
        return product.getVariants().stream()
                .filter(variant -> !variant.isDeleted())
                .map(this::effectivePrice)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    @org.mapstruct.Named("extractBrandName")
    default String extractBrandName(Product product) {
        return product.getBrand() != null && !product.getBrand().isDeleted()
                ? product.getBrand().getName()
                : null;
    }

    @org.mapstruct.Named("extractCategoryNames")
    default java.util.List<String> extractCategoryNames(Product product) {
        return product.getCategories().stream()
                .filter(category -> !category.isDeleted())
                .map(com.locnguyen.ecommerce.domains.category.entity.Category::getName)
                .collect(Collectors.toList());
    }

    /**
     * Returns salePrice if set, otherwise basePrice.
     */
    private BigDecimal effectivePrice(ProductVariant v) {
        return v.getSalePrice() != null ? v.getSalePrice() : v.getBasePrice();
    }
}
