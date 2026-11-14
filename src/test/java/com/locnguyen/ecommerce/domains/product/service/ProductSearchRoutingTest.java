package com.locnguyen.ecommerce.domains.product.service;

import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.brand.mapper.BrandMapper;
import com.locnguyen.ecommerce.domains.brand.repository.BrandRepository;
import com.locnguyen.ecommerce.domains.category.mapper.CategoryMapper;
import com.locnguyen.ecommerce.domains.category.repository.CategoryRepository;
import com.locnguyen.ecommerce.domains.product.dto.ProductFilter;
import com.locnguyen.ecommerce.domains.product.dto.ProductDetailResponse;
import com.locnguyen.ecommerce.domains.product.dto.ProductListItemResponse;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.product.enums.ProductStatus;
import com.locnguyen.ecommerce.domains.productvariant.entity.ProductVariant;
import com.locnguyen.ecommerce.domains.product.mapper.ProductMapper;
import com.locnguyen.ecommerce.domains.product.mapper.ProductVariantMapper;
import com.locnguyen.ecommerce.domains.product.repository.ProductRepository;
import com.locnguyen.ecommerce.domains.product.service.impl.ProductServiceImpl;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@code ProductServiceImpl} routes list queries between the
 * Specification path (no keyword) and the FULLTEXT path (keyword present).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductSearchRoutingTest {

    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock BrandRepository brandRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ProductMapper productMapper;
    @Mock BrandMapper brandMapper;
    @Mock CategoryMapper categoryMapper;
    @Mock ProductVariantMapper productVariantMapper;
    @Mock AuditLogService auditLogService;
    @Mock ProductSearchTextBuilder productSearchTextBuilder;

    @InjectMocks ProductServiceImpl service;

    private final Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    @Test
    void blank_keyword_uses_specification_path() {
        ProductFilter filter = ProductFilter.builder()
                .keyword("   ")
                .brandId(null)
                .build();

        Page<Product> empty = new PageImpl<>(List.of(), pageable, 0);
        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(empty);
        when(productMapper.toListItem(any())).thenReturn(mock(ProductListItemResponse.class));

        service.getAllProducts(filter, pageable);

        verify(productRepository).findAll(any(Specification.class), eq(pageable));
        verify(productRepository, never()).searchByKeyword(any(), any(), any());
    }

    @Test
    void keyword_with_text_uses_fulltext_path() {
        ProductFilter filter = ProductFilter.builder()
                .keyword("Áo Thun")
                .build();

        when(productRepository.searchByKeyword(any(), eq(filter), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getAllProducts(filter, pageable);

        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        verify(productRepository).searchByKeyword(keywordCaptor.capture(), eq(filter), eq(pageable));
        verify(productRepository, never()).findAll(any(Specification.class), any(Pageable.class));

        String booleanKeyword = keywordCaptor.getValue();
        assertThat(booleanKeyword)
                .as("normalized + boolean-mode keyword should be accent-stripped and prefix-matched")
                .contains("+ao*")
                .contains("+thun*");
    }

    @Test
    void published_endpoint_forces_status_published_and_uses_fulltext_when_keyword_present() {
        ProductFilter input = ProductFilter.builder()
                .keyword("Đầm")
                .minPrice(new BigDecimal("100000"))
                .build();

        when(productRepository.searchByKeyword(any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getPublishedProducts(input, pageable);

        ArgumentCaptor<ProductFilter> filterCaptor = ArgumentCaptor.forClass(ProductFilter.class);
        verify(productRepository).searchByKeyword(any(), filterCaptor.capture(), eq(pageable));
        ProductFilter forwarded = filterCaptor.getValue();
        assertThat(forwarded.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(forwarded.getMinPrice()).isEqualByComparingTo("100000");
    }

    @Test
    void product_slug_lookup_uses_direct_repository_query() {
        Product product = new Product();
        UUID productId = UUID.randomUUID();
        ReflectionTestUtils.setField(product, "id", productId);
        product.setName("Direct lookup product");
        product.setSlug("direct-lookup-product");
        product.setStatus(ProductStatus.PUBLISHED);

        when(productRepository.findDetailBySlug("direct-lookup-product")).thenReturn(Optional.of(product));

        ProductDetailResponse response = service.getProductBySlug("direct-lookup-product");

        assertThat(response.getId()).isEqualTo(productId);
        assertThat(response.getSlug()).isEqualTo("direct-lookup-product");
        verify(productRepository).findDetailBySlug("direct-lookup-product");
        verify(productRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void product_slug_lookup_derives_price_range_from_non_deleted_variants() {
        Product product = new Product();
        product.setName("Priced product");
        product.setSlug("priced-product");
        product.setStatus(ProductStatus.PUBLISHED);

        ProductVariant saleVariant = new ProductVariant();
        saleVariant.setBasePrice(new BigDecimal("300000"));
        saleVariant.setSalePrice(new BigDecimal("250000"));
        ProductVariant baseVariant = new ProductVariant();
        baseVariant.setBasePrice(new BigDecimal("320000"));
        product.getVariants().add(saleVariant);
        product.getVariants().add(baseVariant);

        when(productRepository.findDetailBySlug("priced-product")).thenReturn(Optional.of(product));

        ProductDetailResponse response = service.getProductBySlug("priced-product");

        assertThat(response.getMinPrice()).isEqualByComparingTo("250000");
        assertThat(response.getMaxPrice()).isEqualByComparingTo("320000");
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }
}
