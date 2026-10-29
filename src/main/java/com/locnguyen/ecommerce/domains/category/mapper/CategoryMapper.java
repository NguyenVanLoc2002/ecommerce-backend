package com.locnguyen.ecommerce.domains.category.mapper;

import com.locnguyen.ecommerce.domains.category.dto.CategoryResponse;
import com.locnguyen.ecommerce.domains.category.dto.UpdateCategoryRequest;
import com.locnguyen.ecommerce.domains.category.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "parentId",
            expression = "java(category.getParent() != null ? category.getParent().getId() : null)")
    CategoryResponse toResponse(Category category);

    void updateEntity(UpdateCategoryRequest request, @MappingTarget Category category);
}
