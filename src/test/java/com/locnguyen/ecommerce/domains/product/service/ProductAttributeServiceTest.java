package com.locnguyen.ecommerce.domains.product.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.product.dto.attribute.*;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttribute;
import com.locnguyen.ecommerce.domains.product.entity.ProductAttributeValue;
import com.locnguyen.ecommerce.domains.product.enums.AttributeType;
import com.locnguyen.ecommerce.domains.product.mapper.ProductAttributeMapper;
import com.locnguyen.ecommerce.domains.product.repository.ProductAttributeRepository;
import com.locnguyen.ecommerce.domains.product.repository.ProductAttributeValueRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAttributeServiceTest {

    private static UUID uuid(long n) { return new UUID(0L, n); }

    @Mock ProductAttributeRepository attributeRepository;
    @Mock ProductAttributeValueRepository valueRepository;
    @Spy ProductAttributeMapper attributeMapper = new ProductAttributeMapper();

    @InjectMocks ProductAttributeService service;

    // ─── factories ───────────────────────────────────────────────────────────

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
        attribute.getValues().add(v);
        return v;
    }

    private CreateProductAttributeValueRequest valueRequest(String value, String display) {
        CreateProductAttributeValueRequest r = new CreateProductAttributeValueRequest();
        r.setValue(value);
        r.setDisplayValue(display);
        return r;
    }

    // ─── createAttribute ─────────────────────────────────────────────────────

    @Nested
    class CreateAttribute {

        @Test
        void normalises_code_and_seeds_values() {
            CreateProductAttributeRequest request = new CreateProductAttributeRequest();
            request.setName("Color");
            request.setCode("color");
            request.setType(AttributeType.VARIANT);
            request.setValues(List.of(valueRequest("White", "Trắng"), valueRequest("Black", "Đen")));

            when(attributeRepository.existsByCode("COLOR")).thenReturn(false);
            when(attributeRepository.save(any(ProductAttribute.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductAttributeResponse result = service.createAttribute(request);

            assertThat(result.getCode()).isEqualTo("COLOR");
            assertThat(result.getValues()).extracting("value")
                    .containsExactlyInAnyOrder("White", "Black");
        }

        @Test
        void rejects_duplicate_code() {
            CreateProductAttributeRequest request = new CreateProductAttributeRequest();
            request.setName("Color");
            request.setCode("Color");
            request.setType(AttributeType.VARIANT);
            when(attributeRepository.existsByCode("COLOR")).thenReturn(true);

            assertThatThrownBy(() -> service.createAttribute(request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_ATTRIBUTE_CODE_ALREADY_EXISTS);
        }

        @Test
        void rejects_duplicate_values_in_request() {
            CreateProductAttributeRequest request = new CreateProductAttributeRequest();
            request.setName("Color");
            request.setCode("color");
            request.setType(AttributeType.VARIANT);
            request.setValues(List.of(valueRequest("White", null), valueRequest("white", null)));
            when(attributeRepository.existsByCode("COLOR")).thenReturn(false);

            assertThatThrownBy(() -> service.createAttribute(request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_ALREADY_EXISTS);
        }
    }

    // ─── addValue ────────────────────────────────────────────────────────────

    @Nested
    class AddValue {

        @Test
        void adds_value_to_existing_attribute() {
            ProductAttribute color = attribute(uuid(1), "Color", "COLOR", AttributeType.VARIANT);
            when(attributeRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(color));
            when(valueRepository.existsByAttributeIdAndValue(uuid(1), "Navy")).thenReturn(false);
            when(valueRepository.save(any(ProductAttributeValue.class))).thenAnswer(inv -> {
                ProductAttributeValue v = inv.getArgument(0);
                ReflectionTestUtils.setField(v, "id", uuid(50));
                return v;
            });

            service.addValue(uuid(1), valueRequest("Navy", "Xanh hải quân"));

            ArgumentCaptor<ProductAttributeValue> captor = ArgumentCaptor.forClass(ProductAttributeValue.class);
            verify(valueRepository).save(captor.capture());
            assertThat(captor.getValue().getValue()).isEqualTo("Navy");
            assertThat(captor.getValue().getDisplayValue()).isEqualTo("Xanh hải quân");
            assertThat(captor.getValue().getAttribute()).isSameAs(color);
        }

        @Test
        void rejects_duplicate_value_under_same_attribute() {
            ProductAttribute color = attribute(uuid(1), "Color", "COLOR", AttributeType.VARIANT);
            when(attributeRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(color));
            when(valueRepository.existsByAttributeIdAndValue(uuid(1), "White")).thenReturn(true);

            assertThatThrownBy(() -> service.addValue(uuid(1), valueRequest("White", null)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_ALREADY_EXISTS);
            verify(valueRepository, never()).save(any());
        }
    }

    // ─── deleteValue ─────────────────────────────────────────────────────────

    @Nested
    class DeleteValue {

        @Test
        void rejects_when_value_in_use_by_a_variant() {
            ProductAttribute color = attribute(uuid(1), "Color", "COLOR", AttributeType.VARIANT);
            ProductAttributeValue white = value(uuid(2), color, "White", "Trắng");
            when(valueRepository.findByIdAndDeletedFalse(uuid(2))).thenReturn(Optional.of(white));
            when(valueRepository.isUsedByAnyVariant(uuid(2))).thenReturn(true);

            assertThatThrownBy(() -> service.deleteValue(uuid(1), uuid(2)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_IN_USE);
        }

        @Test
        void soft_deletes_when_not_in_use() {
            ProductAttribute color = attribute(uuid(1), "Color", "COLOR", AttributeType.VARIANT);
            ProductAttributeValue white = value(uuid(2), color, "White", "Trắng");
            when(valueRepository.findByIdAndDeletedFalse(uuid(2))).thenReturn(Optional.of(white));
            when(valueRepository.isUsedByAnyVariant(uuid(2))).thenReturn(false);
            when(valueRepository.save(any(ProductAttributeValue.class))).thenAnswer(inv -> inv.getArgument(0));

            service.deleteValue(uuid(1), uuid(2));

            assertThat(white.isDeleted()).isTrue();
            verify(valueRepository).save(white);
        }
    }

    // ─── updateAttribute ─────────────────────────────────────────────────────

    @Nested
    class UpdateAttribute {

        @Test
        void blocks_value_removal_when_value_in_use() {
            ProductAttribute color = attribute(uuid(1), "Color", "COLOR", AttributeType.VARIANT);
            ProductAttributeValue white = value(uuid(2), color, "White", "Trắng");
            when(attributeRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(color));
            when(valueRepository.isUsedByAnyVariant(uuid(2))).thenReturn(true);

            UpdateProductAttributeRequest request = new UpdateProductAttributeRequest();
            // No "White" in the new list -> would be removed
            request.setValues(List.of(valueRequest("Black", "Đen")));

            assertThatThrownBy(() -> service.updateAttribute(uuid(1), request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_ATTRIBUTE_VALUE_IN_USE);
            assertThat(white.isDeleted()).isFalse();
        }
    }

    // ─── getAttributes ───────────────────────────────────────────────────────

    @Nested
    class GetAttribute {

        @Test
        void returns_not_found_for_soft_deleted_attribute() {
            when(attributeRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAttribute(uuid(1)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_ATTRIBUTE_NOT_FOUND);
        }

        @Test
        void excludes_soft_deleted_values_from_response() {
            ProductAttribute color = attribute(uuid(1), "Color", "COLOR", AttributeType.VARIANT);
            value(uuid(2), color, "White", "Tráº¯ng");
            ProductAttributeValue black = value(uuid(3), color, "Black", "Äen");
            black.softDelete("tester");
            when(attributeRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(color));

            ProductAttributeResponse result = service.getAttribute(uuid(1));

            assertThat(result.getValues()).extracting(ProductAttributeValueResponse::getValue)
                    .containsExactly("White");
        }
    }

    @Nested
    class DeleteAttribute {

        @Test
        void soft_deletes_attribute_and_active_values() {
            ProductAttribute color = attribute(uuid(1), "Color", "COLOR", AttributeType.VARIANT);
            ProductAttributeValue white = value(uuid(2), color, "White", "Tráº¯ng");
            ProductAttributeValue black = value(uuid(3), color, "Black", "Äen");
            black.softDelete("tester");
            when(attributeRepository.findByIdAndDeletedFalse(uuid(1))).thenReturn(Optional.of(color));
            when(attributeRepository.save(any(ProductAttribute.class))).thenAnswer(inv -> inv.getArgument(0));

            service.deleteAttribute(uuid(1));

            assertThat(color.isDeleted()).isTrue();
            assertThat(white.isDeleted()).isTrue();
            assertThat(black.isDeleted()).isTrue();
            verify(attributeRepository).save(color);
        }
    }

    @Nested
    class ListAttributes {

        @Test
        void filters_by_type() {
            ProductAttribute color = attribute(uuid(1), "Color", "COLOR", AttributeType.VARIANT);
            value(uuid(2), color, "White", "White");
            ProductAttributeValue black = value(uuid(3), color, "Black", "Black");
            black.softDelete("tester");
            Page<ProductAttribute> page = new PageImpl<>(List.of(color));
            when(attributeRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(page);

            ProductAttributeFilter filter = new ProductAttributeFilter();
            filter.setType(AttributeType.VARIANT);

            assertThat(service.getAttributes(filter, PageRequest.of(0, 20)).getItems())
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.getCode()).isEqualTo("COLOR");
                        assertThat(item.getValues()).extracting(ProductAttributeValueResponse::getValue)
                                .containsExactly("White");
                    });
        }
    }
}
