package com.locnguyen.ecommerce.common.utils;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Utility for generating URL-friendly slugs.
 *
 * <p>Handles Vietnamese diacritics correctly using Unicode NFD normalization.
 *
 * <p>Examples:
 * <pre>
 *   "Áo Thun Basic Nam"     → "ao-thun-basic-nam"
 *   "Quần Jean Slim Fit"    → "quan-jean-slim-fit"
 *   "LOCAL BRAND  Việt Nam" → "local-brand-viet-nam"
 * </pre>
 */
public final class SlugUtils {

    private SlugUtils() {}

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile("[^a-z0-9]+");

    private static final Pattern MULTIPLE_HYPHENS =
            Pattern.compile("-{2,}");

    private static final int MAX_SLUG_LENGTH = 200;

    /**
     * Convert any string to a URL-safe slug.
     *
     * @param input raw text (may contain Vietnamese, spaces, special chars)
     * @return lowercase hyphen-separated slug, or empty string if input is blank
     */
    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // 1. Decompose composed characters (á → a + combining acute)
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD);

        // 2. Remove combining diacritics (the accent marks)
        String ascii = DIACRITICS.matcher(normalized).replaceAll("");

        // 3. Lowercase
        String lower = ascii.toLowerCase();

        // 4. Replace any non-alphanumeric character with hyphen
        String slug = NON_ALPHANUMERIC.matcher(lower).replaceAll("-");

        // 5. Collapse consecutive hyphens
        slug = MULTIPLE_HYPHENS.matcher(slug).replaceAll("-");

        // 6. Strip leading / trailing hyphens
        slug = slug.replaceAll("^-+|-+$", "");

        // 7. Enforce max length (trim at word boundary if possible)
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH);
            int lastHyphen = slug.lastIndexOf('-');
            if (lastHyphen > MAX_SLUG_LENGTH / 2) {
                slug = slug.substring(0, lastHyphen);
            }
        }

        return slug;
    }

    /**
     * Append a numeric suffix to make a slug unique.
     * Example: "ao-thun" + 2 → "ao-thun-2"
     */
    public static String withSuffix(String slug, long suffix) {
        return slug + "-" + suffix;
    }
}
