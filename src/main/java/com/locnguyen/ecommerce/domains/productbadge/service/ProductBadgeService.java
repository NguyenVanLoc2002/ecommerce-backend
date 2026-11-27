package com.locnguyen.ecommerce.domains.productbadge.service;

import com.locnguyen.ecommerce.domains.productbadge.dto.ProductBadgeModels.*;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProductBadgeService {
    PagedResponse<Badge> list(Pageable page);
    Badge get(UUID id);
    Badge create(Input input);
    Badge update(UUID id, Input input);
    PagedResponse<ProductReference> products(UUID id, Pageable page);
    void assign(UUID id, UUID productId);
    void unassign(UUID id, UUID productId);
    Map<UUID, List<Display>> findActive(List<UUID> productIds, int limit);
}
