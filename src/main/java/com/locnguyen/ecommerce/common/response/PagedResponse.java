package com.locnguyen.ecommerce.common.response;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Paginated response data container.
 *
 * <pre>
 * {
 *   "items": [...],
 *   "page": 0,
 *   "size": 20,
 *   "totalItems": 125,
 *   "totalPages": 7,
 *   "hasNext": true,
 *   "hasPrevious": false
 * }
 * </pre>
 *
 * Wrap in {@link ApiResponse}: {@code ApiResponse.success(PagedResponse.of(page))}
 */
@Getter
public class PagedResponse<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrevious;

    private PagedResponse(List<T> items, int page, int size,
                          long totalItems, int totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
        this.hasNext = page < totalPages - 1;
        this.hasPrevious = page > 0;
    }

    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    public static <T> PagedResponse<T> of(List<T> items, Page<?> page) {
        return new PagedResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
