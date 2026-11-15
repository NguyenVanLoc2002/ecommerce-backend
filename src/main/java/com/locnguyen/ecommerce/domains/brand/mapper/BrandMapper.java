package com.locnguyen.ecommerce.domains.brand.mapper;

import com.locnguyen.ecommerce.domains.brand.dto.BrandResponse;
import com.locnguyen.ecommerce.domains.brand.dto.UpdateBrandRequest;
import com.locnguyen.ecommerce.domains.brand.entity.Brand;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BrandMapper {

    @Mapping(target = "logoStorageKey", source = "assetStorageKey")
    BrandResponse toResponse(Brand brand);

    void updateEntity(UpdateBrandRequest request, @MappingTarget Brand brand);
}
