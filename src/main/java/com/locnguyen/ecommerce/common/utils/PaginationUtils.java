package com.locnguyen.ecommerce.common.utils;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Utility for building safe {@link Pageable} objects.
 *
 * <p>Ensures page/size values are always within acceptable bounds,
 * and sort fields are validated against an allowed whitelist to prevent
 * injection via arbitrary sort parameters.
 */
public final class PaginationUtils {

    private PaginationUtils() {}

    /**
     * Build a {@link Pageable} with default sort by {@code createdAt DESC}.
     *
     * @param page zero-based page index (negative values clamped to 0)
     * @param size page size (clamped between 1 and {@code MAX_PAGE_SIZE})
     */
    public static Pageable build(int page, int size) {
        return PageRequest.of(
                clampPage(page),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    /**
     * Build a {@link Pageable} with an explicit {@link Sort}.
     */
    public static Pageable build(int page, int size, Sort sort) {
        return PageRequest.of(clampPage(page), clampSize(size), sort);
    }

    /**
     * Build a {@link Pageable} with sort field validation.
     *
     * <p>If {@code sortBy} is not in {@code allowedFields}, falls back to {@code createdAt}.
     *
     * @param page          zero-based page index
     * @param size          page size
     * @param sortBy        field name to sort by
     * @param direction     "asc" or "desc" (case-insensitive); defaults to desc
     * @param allowedFields whitelist of valid sortable field names
     */
    public static Pageable build(int page, int size,
                                  String sortBy, String direction,
                                  Set<String> allowedFields) {
        String safeField = (sortBy != null && allowedFields.contains(sortBy))
                ? sortBy
                : "createdAt";

        Sort.Direction safeDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(clampPage(page), clampSize(size),
                Sort.by(safeDirection, safeField));
    }

    /**
     * Build a {@link Sort} from raw string parameters with field validation.
     */
    public static Sort toSort(String sortBy, String direction, Set<String> allowedFields) {
        String safeField = (sortBy != null && allowedFields.contains(sortBy))
                ? sortBy
                : "createdAt";

        Sort.Direction safeDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(safeDirection, safeField);
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    private static int clampPage(int page) {
        return Math.max(0, page);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(1, size), AppConstants.MAX_PAGE_SIZE);
    }
}
