package com.locnguyen.ecommerce.domains.productbadge.service.impl;

import com.locnguyen.ecommerce.domains.productbadge.dto.ProductBadgeModels.*;
import com.locnguyen.ecommerce.domains.productbadge.repository.ProductBadgeRepository;
import com.locnguyen.ecommerce.domains.productbadge.service.ProductBadgeService;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductBadgeServiceImpl implements ProductBadgeService {
    private static final Set<String> RESERVED_CODES = Set.of("SALE", "DISCOUNT", "OUT_OF_STOCK", "LOW_STOCK");
    private final ProductBadgeRepository repository;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<Badge> list(Pageable page) {
        return PagedResponse.of(new PageImpl<>(repository.list(Math.toIntExact(page.getOffset()), page.getPageSize()),
                page, repository.count()));
    }

    @Override
    public Badge get(UUID id) {
        return repository.find(id).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Badge not found"));
    }

    @Override
    @Transactional
    public Badge create(Input input) {
        validateCode(input);
        UUID id = UUID.randomUUID();
        try {
            repository.insert(id, input);
        } catch (DuplicateKeyException ex) {
            throw new AppException(ErrorCode.PRODUCT_BADGE_CODE_ALREADY_EXISTS);
        }
        return get(id);
    }

    @Override
    @Transactional
    public Badge update(UUID id, Input input) {
        get(id);
        validateCode(input);
        if (input.version() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Version is required when updating a badge");
        }
        try {
            if (repository.update(id, input) == 0) {
                throw new AppException(ErrorCode.CONCURRENT_MODIFICATION, "Badge changed. Reload it before saving.");
            }
        } catch (DuplicateKeyException ex) {
            throw new AppException(ErrorCode.PRODUCT_BADGE_CODE_ALREADY_EXISTS);
        }
        return get(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductReference> products(UUID id, Pageable page) {
        get(id);
        return PagedResponse.of(new PageImpl<>(
                repository.assignedProducts(id, Math.toIntExact(page.getOffset()), page.getPageSize()),
                page, repository.countAssigned(id)));
    }

    @Override
    @Transactional
    public void assign(UUID id, UUID productId) {
        if (!get(id).active()) throw new AppException(ErrorCode.VALIDATION_ERROR, "Activate this badge before assigning it");
        if (!repository.productExists(productId)) throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        repository.link(id, productId);
    }

    @Override
    @Transactional
    public void unassign(UUID id, UUID productId) {
        get(id);
        repository.unlink(id, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<Display>> findActive(List<UUID> productIds, int limit) {
        return repository.findActive(productIds, limit);
    }

    private static void validateCode(Input input) {
        if (RESERVED_CODES.contains(input.code())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "This badge code is reserved for commerce states");
        }
    }
}
