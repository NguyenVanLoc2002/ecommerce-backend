package com.locnguyen.ecommerce.domains.sizeguide.service;

import com.locnguyen.ecommerce.domains.sizeguide.dto.SizeGuideModels.*;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface SizeGuideService {
    PagedResponse<Summary> list(String search, Pageable pageable);
    Guide get(UUID id);
    Guide create(Input input);
    Guide update(UUID id, Input input);
    Guide copy(UUID id);
    List<Assignment> assignments(UUID id);
    void assign(UUID id, AssignmentInput input);
    void unassign(UUID id, String key);
    Display resolve(UUID productId);
}
