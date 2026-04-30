package com.locnguyen.ecommerce.domains.productvariant.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
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
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    private static UUID uuid(long n) { return new UUID(0L, n); }

    @Mock ProductVariantRepository variantRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductAttributeValueRepository attributeValueRepository;
    @Spy  ProductVariantMapper variantMapper = new ProductVariantMapper();

    @InjectMocks ProductVariantService service;

    // ─── factories ───────────────────────────────────────────────────────────

    private Product product(UUID id, String slug) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setName("Áo thun nam");
        product.setSlug(slug);
        return product;
    }

    private ProductAttribute attribute(UUID id, String name, String code, AttributeType type) {
        ProductAttribute attribute = new ProductAttribute();
        ReflectionTestUtils.setField(attribute, "id", id);
        attribute.setName(name);
        attribute.setCode(code);
        attribute.setType(type);
        return attribute;
    }

    private ProductAttributeValue value(UUID id, ProductAttribute attribute, String value, String displayValue) {
        ProductAttributeValue v = new ProductAttributeValue();
        ReflectionTestUtils.setField(v, "id", id);
        v.setAttribute(attribute);
        v.setValue(value);
        v.setDisplayValue(displayValue);
        return v;
    }

    private CreateVariantRequest createRequest(BigDecimal base, Set<UUID> valueIds) {
        CreateVariantRequest request = new CreateVariantRequest();
        request.setBasePrice(base);
        request.setAttributeValueIds(valueIds);
        return request;
    }

    private void stubProductSave() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubVariantSave() {
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> {
            ProductVariant v = inv.getArgument(0);
            if (v.getId() == null) {
                ReflectionTestUtils.setField(v, "id", uuid(900));
            }
            return v;
        });
    }

    // ─── createVariant ───────────────────────────────────────────────────────

    @Nested
    class CreateVariant {

        @Test
        void creates_variant_with_attribute_value_ids_and_auto_generates_artifacts() {
            Product product = product(uuid(1), "ao-thun-nam");
            ProductAttribute color = attribute(uuid(10), "Color", "COLOR", AttributeType.VARIANT);
            ProductAttribute size = attribute(uuid(11), "Size", "SIZE", AttributeType.VARIANT);
            ProductAttributeValue white = value(uuid(20), color, "White", "Trắng");
            ProductAttributeValue m = value(uuid(21), size, "M", "M");

            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));
            when(attributeValueRepository.findByIdIn(anyCollection())).thenReturn(List.of(white, m));
            when(variantRepository.findVariantIdsWithExactAttributeSet(any(), anyCollection(), anyLong()))
                    .thenReturn(List.of());
            // No SKU/barcode collisions
            when(variantRepository.findBySku(anyString())).thenReturn(Optional.empty());
            stubVariantSave();
            stubProductSave();

            CreateVariantRequest request = createRequest(new BigDecimal("200000"),
                    Set.of(uuid(20), uuid(21)));

            VariantResponse response = service.createVariant(uuid(1), request);

            ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
            verify(variantRepository).save(captor.capture());
            ProductVariant saved = captor.getValue();
            assertThat(saved.getSku()).startsWith("AO-THUN-NAM");
            assertThat(saved.getVariantName()).isEqualTo("Trắng / M");
            assertThat(saved.getAttributeValues()).extracting(ProductAttributeValue::getId)
                    .containsExactlyInAnyOrder(uuid(20), uuid(21));
            assertThat(response.getProductId()).isEqualTo(uuid(1));
            assertThat(response.getAttributes()).hasSize(2);
        }

        @Test
        void uses_manual_sku_when_unique() {
            Product product = product(uuid(1), "ao-thun-nam");
            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));
            // Empty attributeValueIds -> findByIdIn / findVariantIdsWithExactAttributeSet
            // are not called by the service.
            when(variantRepository.findBySku("MANUAL-SKU-001")).thenReturn(Optional.empty());
            stubVariantSave();
            stubProductSave();

            CreateVariantRequest request = createRequest(new BigDecimal("100000"), Set.of());
            request.setSku("MANUAL-SKU-001");
            request.setVariantName("Default");

            service.createVariant(uuid(1), request);

            verify(variantRepository).save(any(ProductVariant.class));
        }

        @Test
        void rejects_manual_sku_collision() {
            Product product = product(uuid(1), "ao-thun-nam");
            ProductVariant existing = new ProductVariant();
            ReflectionTestUtils.setField(existing, "id", uuid(99));
            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));
            when(variantRepository.findBySku("DUP-SKU")).thenReturn(Optional.of(existing));

            CreateVariantRequest request = createRequest(new BigDecimal("100000"), Set.of());
            request.setSku("DUP-SKU");
            request.setVariantName("Default");

            assertThatThrownBy(() -> service.createVariant(uuid(1), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SKU_ALREADY_EXISTS);
        }

        @Test
        void rejects_descriptive_attribute_value() {
            Product product = product(uuid(1), "ao-thun-nam");
            ProductAttribute material = attribute(uuid(10), "Material", "MATERIAL", AttributeType.DESCRIPTIVE);
            ProductAttributeValue cotton = value(uuid(20), material, "Cotton", "Cotton");

            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));
            when(attributeValueRepository.findByIdIn(anyCollection())).thenReturn(List.of(cotton));

            CreateVariantRequest request = createRequest(new BigDecimal("100000"), Set.of(uuid(20)));

            assertThatThrownBy(() -> service.createVariant(uuid(1), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VARIANT_ATTRIBUTE_INVALID);
        }

        @Test
        void rejects_two_values_from_the_same_attribute() {
            Product product = product(uuid(1), "ao-thun-nam");
            ProductAttribute color = attribute(uuid(10), "Color", "COLOR", AttributeType.VARIANT);
            ProductAttributeValue white = value(uuid(20), color, "White", "Trắng");
            ProductAttributeValue black = value(uuid(21), color, "Black", "Đen");

            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));
            when(attributeValueRepository.findByIdIn(anyCollection())).thenReturn(List.of(white, black));

            CreateVariantRequest request = createRequest(new BigDecimal("100000"),
                    Set.of(uuid(20), uuid(21)));

            assertThatThrownBy(() -> service.createVariant(uuid(1), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VARIANT_ATTRIBUTE_INVALID);
        }

        @Test
        void rejects_unknown_attribute_value_id() {
            Product product = product(uuid(1), "ao-thun-nam");
            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));
            when(attributeValueRepository.findByIdIn(anyCollection())).thenReturn(List.of());

            CreateVariantRequest request = createRequest(new BigDecimal("100000"), Set.of(uuid(20)));

            assertThatThrownBy(() -> service.createVariant(uuid(1), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_NOT_FOUND);
        }

        @Test
        void rejects_duplicate_combination_for_same_product() {
            Product product = product(uuid(1), "ao-thun-nam");
            ProductAttribute color = attribute(uuid(10), "Color", "COLOR", AttributeType.VARIANT);
            ProductAttributeValue white = value(uuid(20), color, "White", "Trắng");

            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));
            when(attributeValueRepository.findByIdIn(anyCollection())).thenReturn(List.of(white));
            when(variantRepository.findVariantIdsWithExactAttributeSet(any(), anyCollection(), anyLong()))
                    .thenReturn(List.of(uuid(500)));

            CreateVariantRequest request = createRequest(new BigDecimal("100000"), Set.of(uuid(20)));

            assertThatThrownBy(() -> service.createVariant(uuid(1), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VARIANT_COMBINATION_DUPLICATE);
        }

        @Test
        void rejects_sale_price_greater_than_base_price() {
            Product product = product(uuid(1), "ao-thun-nam");
            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));

            CreateVariantRequest request = createRequest(new BigDecimal("100000"), Set.of());
            request.setSalePrice(new BigDecimal("150000"));

            assertThatThrownBy(() -> service.createVariant(uuid(1), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VARIANT_INVALID_PRICE);
        }

        @Test
        void rejects_compare_at_price_below_base_price() {
            Product product = product(uuid(1), "ao-thun-nam");
            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));

            CreateVariantRequest request = createRequest(new BigDecimal("100000"), Set.of());
            request.setCompareAtPrice(new BigDecimal("80000"));

            assertThatThrownBy(() -> service.createVariant(uuid(1), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VARIANT_INVALID_PRICE);
        }

        @Test
        void rejects_zero_or_negative_weight() {
            Product product = product(uuid(1), "ao-thun-nam");
            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));

            CreateVariantRequest request = createRequest(new BigDecimal("100000"), Set.of());
            request.setWeightGram(0);

            assertThatThrownBy(() -> service.createVariant(uuid(1), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.VARIANT_INVALID_WEIGHT);
        }

        @Test
        void auto_generates_barcode_when_requested() {
            Product product = product(uuid(1), "ao-thun-nam");
            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));
            when(variantRepository.findBySku(anyString())).thenReturn(Optional.empty());
            when(variantRepository.existsByBarcode(anyString())).thenReturn(false);
            stubVariantSave();
            stubProductSave();

            CreateVariantRequest request = createRequest(new BigDecimal("100000"), Set.of());
            request.setAutoGenerateBarcode(true);
            request.setVariantName("Default");

            ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
            service.createVariant(uuid(1), request);
            verify(variantRepository).save(captor.capture());

            assertThat(captor.getValue().getBarcode()).isNotBlank();
        }

        @Test
        void rejects_manual_barcode_collision() {
            Product product = product(uuid(1), "ao-thun-nam");
            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));
            when(variantRepository.findBySku(anyString())).thenReturn(Optional.empty());
            when(variantRepository.existsByBarcode("DUP-BC")).thenReturn(true);

            CreateVariantRequest request = createRequest(new BigDecimal("100000"), Set.of());
            request.setBarcode("DUP-BC");
            request.setVariantName("Default");

            assertThatThrownBy(() -> service.createVariant(uuid(1), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BARCODE_ALREADY_EXISTS);
        }

        @Test
        void auto_generates_variant_name_from_attribute_display_values() {
            Product product = product(uuid(1), "ao-thun-nam");
            ProductAttribute color = attribute(uuid(10), "Color", "COLOR", AttributeType.VARIANT);
            ProductAttribute size = attribute(uuid(11), "Size", "SIZE", AttributeType.VARIANT);
            ProductAttributeValue white = value(uuid(20), color, "White", "Trắng");
            ProductAttributeValue m = value(uuid(21), size, "M", null);

            when(productRepository.findById(uuid(1))).thenReturn(Optional.of(product));
            when(attributeValueRepository.findByIdIn(anyCollection())).thenReturn(List.of(white, m));
            when(variantRepository.findVariantIdsWithExactAttributeSet(any(), anyCollection(), anyLong()))
                    .thenReturn(List.of());
            when(variantRepository.findBySku(anyString())).thenReturn(Optional.empty());
            stubVariantSave();
            stubProductSave();

            CreateVariantRequest request = createRequest(new BigDecimal("100000"),
                    Set.of(uuid(20), uuid(21)));
            request.setAutoGenerateVariantName(true);

            ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
            service.createVariant(uuid(1), request);
            verify(variantRepository).save(captor.capture());

            // Display value falls back to value when null — Color/Size names sort alphabetically
            assertThat(captor.getValue().getVariantName()).isEqualTo("Trắng / M");
        }
    }

    // ─── updateVariant ───────────────────────────────────────────────────────

    @Nested
    class UpdateVariant {

        @Test
        void replaces_attribute_set_and_does_not_conflict_with_self() {
            Product product = product(uuid(1), "ao-thun-nam");
            ProductAttribute color = attribute(uuid(10), "Color", "COLOR", AttributeType.VARIANT);
            ProductAttributeValue white = value(uuid(20), color, "White", "Trắng");
            ProductAttributeValue black = value(uuid(21), color, "Black", "Đen");

            ProductVariant variant = new ProductVariant();
            ReflectionTestUtils.setField(variant, "id", uuid(900));
            variant.setProduct(product);
            variant.setBasePrice(new BigDecimal("100000"));
            variant.setAttributeValues(new HashSet<>(Set.of(white)));

            when(variantRepository.findByIdAndProductId(uuid(900), uuid(1)))
                    .thenReturn(Optional.of(variant));
            when(attributeValueRepository.findByIdIn(anyCollection())).thenReturn(List.of(black));
            // The same variant id may come back; service should ignore self when checking duplicates.
            when(variantRepository.findVariantIdsWithExactAttributeSet(any(), anyCollection(), anyLong()))
                    .thenReturn(List.of(uuid(900)));
            stubVariantSave();

            UpdateVariantRequest request = new UpdateVariantRequest();
            request.setAttributeValueIds(Set.of(uuid(21)));

            service.updateVariant(uuid(1), uuid(900), request);

            assertThat(variant.getAttributeValues()).extracting(ProductAttributeValue::getId)
                    .containsExactly(uuid(21));
        }
    }
}
