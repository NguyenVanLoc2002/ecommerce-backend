package com.locnguyen.ecommerce.domains.category.mapper;

import com.locnguyen.ecommerce.domains.category.dto.CategoryResponse;
import com.locnguyen.ecommerce.domains.category.entity.Category;
import com.locnguyen.ecommerce.domains.category.enums.CategoryStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMapperTest {

    private final CategoryMapper categoryMapper = Mappers.getMapper(CategoryMapper.class);

    @Test
    void toResponse_returnsNullParentIdForRootCategory() {
        Category category = category(
                uuid("0d93e7b4-d1d5-4cad-aea5-20fd52dbb1d2"),
                "Accessories",
                "accessories"
        );

        CategoryResponse response = categoryMapper.toResponse(category);

        assertThat(response.getId()).isEqualTo(category.getId());
        assertThat(response.getParentId()).isNull();
        assertThat(response.getName()).isEqualTo("Accessories");
    }

    @Test
    void toResponse_mapsParentIdForChildCategory() {
        Category parent = category(
                uuid("8c61ef7f-75f9-50f1-a73c-5ee4c15cc749"),
                "Accessories",
                "accessories"
        );
        Category child = category(
                uuid("cf30b139-1002-5da3-95cf-1bc7976ee8f3"),
                "Bags",
                "accessories-bags"
        );
        child.setParent(parent);

        CategoryResponse response = categoryMapper.toResponse(child);

        assertThat(response.getId()).isEqualTo(child.getId());
        assertThat(response.getParentId()).isEqualTo(parent.getId());
        assertThat(response.getSlug()).isEqualTo("accessories-bags");
    }

    private static Category category(UUID id, String name, String slug) {
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "createdAt", LocalDateTime.of(2026, 5, 23, 2, 0));
        category.setName(name);
        category.setSlug(slug);
        category.setStatus(CategoryStatus.ACTIVE);
        category.setSortOrder(0);
        return category;
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
