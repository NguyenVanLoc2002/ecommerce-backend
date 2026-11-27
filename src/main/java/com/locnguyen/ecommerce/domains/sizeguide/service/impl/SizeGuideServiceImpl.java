package com.locnguyen.ecommerce.domains.sizeguide.service.impl;

import com.locnguyen.ecommerce.domains.sizeguide.dto.SizeGuideModels.*;
import com.locnguyen.ecommerce.domains.sizeguide.repository.SizeGuideRepository;
import com.locnguyen.ecommerce.domains.sizeguide.service.SizeGuideService;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SizeGuideServiceImpl implements SizeGuideService {
    private final SizeGuideRepository repository;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<Summary> list(String search, Pageable pageable) {
        String pattern = "%" + search.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return PagedResponse.of(new PageImpl<>(
                repository.summaries(pattern, Math.toIntExact(pageable.getOffset()), pageable.getPageSize()),
                pageable, repository.count(pattern)));
    }

    @Override
    public Guide get(UUID id) {
        return repository.find(id).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Size guide not found"));
    }

    @Override
    @Transactional
    public Guide create(Input input) {
        validateContent(input.content());
        UUID id = UUID.randomUUID();
        repository.insert(id, input);
        return get(id);
    }

    @Override
    @Transactional
    public Guide update(UUID id, Input input) {
        get(id);
        validateContent(input.content());
        if (input.version() == null) {
            throw invalid("Version is required when updating a size guide");
        }
        if (repository.update(id, input) == 0) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION, "Size guide changed. Reload it before saving.");
        }
        return get(id);
    }

    @Override
    @Transactional
    public Guide copy(UUID id) {
        Guide source = get(id);
        String name = source.name();
        if (name.length() > 113) name = name.substring(0, 113);
        return create(new Input(name + " (copy)", false, null, source.content()));
    }

    @Override
    public List<Assignment> assignments(UUID id) {
        get(id);
        return repository.assignments(id);
    }

    @Override
    @Transactional
    public void assign(UUID id, AssignmentInput input) {
        if (!get(id).active()) {
            throw invalid("Activate this size guide before assigning it");
        }
        boolean product = input.productId() != null;
        boolean category = input.categoryId() != null;
        boolean brand = input.brandId() != null;
        String key = switch (input.scope()) {
            case PRODUCT -> product && !category && !brand ? "PRODUCT:" + input.productId() : null;
            case CATEGORY_BRAND -> !product && category && brand ? "CATEGORY_BRAND:" + input.categoryId() + ":" + input.brandId() : null;
            case BRAND -> !product && !category && brand ? "BRAND:" + input.brandId() : null;
            case CATEGORY -> !product && category && !brand ? "CATEGORY:" + input.categoryId() : null;
            case SHOP -> !product && !category && !brand ? "SHOP" : null;
        };
        if (key == null) throw invalid("Assignment target does not match its scope");
        if ((product && !repository.productExists(input.productId()))
                || (category && !repository.categoryExists(input.categoryId()))
                || (brand && !repository.brandExists(input.brandId()))) {
            throw new AppException(ErrorCode.NOT_FOUND, "Assignment target not found");
        }
        repository.assign(id, key, input);
    }

    @Override
    @Transactional
    public void unassign(UUID id, String key) {
        get(id);
        repository.unassign(id, key);
    }

    @Override
    @Transactional(readOnly = true)
    public Display resolve(UUID productId) {
        return repository.resolve(productId).orElse(null);
    }

    private static void validateContent(Content content) {
        var keys = new HashSet<String>();
        for (Column column : content.columns()) {
            if (!keys.add(column.key())) throw invalid("Column keys must be unique");
        }
        var sizes = new HashSet<String>();
        for (Row row : content.rows()) {
            if (!sizes.add(row.size().strip().toUpperCase(Locale.ROOT))) throw invalid("Size labels must be unique");
            if (!keys.equals(row.values().keySet())) throw invalid("Every size must have one value for each measurement column");
            for (Range value : row.values().values()) {
                if (value.max() != null && value.max().compareTo(value.min()) < 0) {
                    throw invalid("Measurement maximum cannot be smaller than the minimum");
                }
            }
        }
        String url = content.measurementImageUrl();
        if (url != null && !url.isBlank()) {
            try {
                URI image = URI.create(url);
                if (!"https".equalsIgnoreCase(image.getScheme()) || image.getHost() == null || image.getUserInfo() != null) {
                    throw invalid("Measurement image must use a public HTTPS URL");
                }
            } catch (IllegalArgumentException ex) {
                throw invalid("Measurement image must use a public HTTPS URL");
            }
        }
    }

    private static AppException invalid(String message) {
        return new AppException(ErrorCode.VALIDATION_ERROR, message);
    }
}
