package com.locnguyen.ecommerce.domains.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.product.controller.ProductController;
import com.locnguyen.ecommerce.domains.product.dto.ProductDetailResponse;
import com.locnguyen.ecommerce.domains.product.dto.ProductFilter;
import com.locnguyen.ecommerce.domains.product.dto.ProductListItemResponse;
import com.locnguyen.ecommerce.domains.productbadge.dto.ProductBadgeModels.Display;
import com.locnguyen.ecommerce.domains.productbadge.service.ProductBadgeService;
import com.locnguyen.ecommerce.domains.sizeguide.service.SizeGuideService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductPresentationContractTest {
    private final ProductService catalog = mock(ProductService.class);
    private final SizeGuideService guides = mock(SizeGuideService.class);
    private final ProductBadgeService badges = mock(ProductBadgeService.class);
    private final ProductController controller = new ProductController(catalog, guides, badges);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void publicListUsesOneBatchAndKeepsPagination() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var filter = ProductFilter.builder().build();
        var pageable = PageRequest.of(1, 2);
        var items = List.of(ProductListItemResponse.builder().id(first).featured(true).build(),
                ProductListItemResponse.builder().id(second).build());
        when(catalog.getPublishedProducts(filter, pageable)).thenReturn(PagedResponse.of(new PageImpl<>(items, pageable, 8)));
        when(badges.findActive(List.of(first, second), 1)).thenReturn(Map.of(first, List.of(new Display("CLASSIC", "Classic"))));

        var data = json.valueToTree(controller.getProducts(filter, pageable)).get("data");

        assertThat(data.get("totalItems").longValue()).isEqualTo(8);
        assertThat(data.get("page").intValue()).isEqualTo(1);
        assertThat(data.get("items").get(0).get("featured").booleanValue()).isTrue();
        assertThat(data.get("items").get(0).get("badge").size()).isEqualTo(2);
        assertThat(data.get("items").get(1).has("badge")).isFalse();
        assertThat(data.get("items").get(0).has("sizeGuide")).isFalse();
        verify(badges).findActive(List.of(first, second), 1);
        verifyNoMoreInteractions(badges);
        verifyNoInteractions(guides);
    }

    @Test
    void publicDetailOmitsUnconfiguredPresentationFields() {
        UUID productId = UUID.randomUUID();
        when(catalog.getProductById(productId)).thenReturn(ProductDetailResponse.builder().id(productId).build());
        when(badges.findActive(List.of(productId), 2)).thenReturn(Map.of());

        var data = json.valueToTree(controller.getProduct(productId)).get("data");

        assertThat(data.has("badges")).isFalse();
        assertThat(data.has("sizeGuide")).isFalse();
        verify(guides).resolve(productId);
    }
}
