package com.locnguyen.ecommerce.domains.product.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.product.dto.CreateProductMediaRequest;
import com.locnguyen.ecommerce.domains.product.dto.MediaResponse;
import com.locnguyen.ecommerce.domains.product.dto.UpdateProductMediaRequest;
import com.locnguyen.ecommerce.domains.product.entity.Product;
import com.locnguyen.ecommerce.domains.product.entity.ProductMedia;
import com.locnguyen.ecommerce.domains.product.enums.MediaType;
import com.locnguyen.ecommerce.domains.product.repository.ProductMediaRepository;
import com.locnguyen.ecommerce.domains.product.repository.ProductRepository;
import com.locnguyen.ecommerce.domains.product.service.impl.ProductMediaServiceImpl;
import com.locnguyen.ecommerce.domains.productvariant.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMediaServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEDIA_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock ProductMediaRepository mediaRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks ProductMediaServiceImpl service;

    @Test
    void createMedia_whenPrimaryImage_clearsExistingProductPrimary() {
        Product product = product(PRODUCT_ID);
        ProductMedia existing = media(UUID.randomUUID(), product, MediaType.IMAGE, true);
        CreateProductMediaRequest request = new CreateProductMediaRequest();
        request.setMediaUrl("https://cdn.example.com/new.jpg");
        request.setPrimary(true);

        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(mediaRepository.findByProductIdAndPrimaryTrue(PRODUCT_ID)).thenReturn(List.of(existing));
        when(mediaRepository.save(any(ProductMedia.class))).thenAnswer(invocation -> {
            ProductMedia saved = invocation.getArgument(0);
            setId(saved, MEDIA_ID);
            return saved;
        });

        MediaResponse response = service.createMedia(PRODUCT_ID, request);

        ArgumentCaptor<ProductMedia> captor = ArgumentCaptor.forClass(ProductMedia.class);
        verify(mediaRepository).save(captor.capture());
        assertThat(existing.isPrimary()).isFalse();
        assertThat(captor.getValue().isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(MEDIA_ID);
        assertThat(response.getMediaType()).isEqualTo("IMAGE");
    }

    @Test
    void createMedia_whenVariantDoesNotBelongToProduct_throwsVariantNotFound() {
        CreateProductMediaRequest request = new CreateProductMediaRequest();
        request.setMediaUrl("https://cdn.example.com/variant.jpg");
        request.setVariantId(UUID.randomUUID());
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(product(PRODUCT_ID)));
        when(variantRepository.findByIdAndProductIdAndDeletedFalse(eq(request.getVariantId()), eq(PRODUCT_ID)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createMedia(PRODUCT_ID, request))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_VARIANT_NOT_FOUND);
    }

    @Test
    void updateMedia_whenPrimaryVideo_throwsValidationError() {
        Product product = product(PRODUCT_ID);
        ProductMedia media = media(MEDIA_ID, product, MediaType.IMAGE, true);
        UpdateProductMediaRequest request = new UpdateProductMediaRequest();
        request.setMediaType(MediaType.VIDEO);

        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(mediaRepository.findByIdAndProductId(MEDIA_ID, PRODUCT_ID)).thenReturn(Optional.of(media));

        assertThatThrownBy(() -> service.updateMedia(PRODUCT_ID, MEDIA_ID, request))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void deleteMedia_whenMediaBelongsToAnotherProduct_throwsMediaNotFound() {
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(product(PRODUCT_ID)));
        when(mediaRepository.findByIdAndProductId(MEDIA_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMedia(PRODUCT_ID, MEDIA_ID))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_MEDIA_NOT_FOUND);
    }

    private static Product product(UUID id) {
        Product product = new Product();
        setId(product, id);
        return product;
    }

    private static ProductMedia media(UUID id, Product product, MediaType type, boolean primary) {
        ProductMedia media = new ProductMedia();
        setId(media, id);
        media.setProduct(product);
        media.setMediaUrl("https://cdn.example.com/existing.jpg");
        media.setMediaType(type);
        media.setSortOrder(0);
        media.setPrimary(primary);
        return media;
    }

    private static void setId(Object entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
