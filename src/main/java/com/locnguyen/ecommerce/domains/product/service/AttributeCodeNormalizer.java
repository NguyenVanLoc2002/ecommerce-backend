package com.locnguyen.ecommerce.domains.product.service;

/**
 * Canonicalises attribute codes to upper snake-case so they remain stable identifiers
 * regardless of how the admin types them in (e.g. {@code "color"}, {@code "Color"},
 * {@code "Sleeve Length"} all become {@code COLOR}, {@code COLOR}, {@code SLEEVE_LENGTH}).
 */
public final class AttributeCodeNormalizer {

    private AttributeCodeNormalizer() {}

    public static String normalize(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        // Replace any run of non-alphanumeric characters with a single underscore,
        // strip leading/trailing underscores, then upper-case.
        String collapsed = trimmed.replaceAll("[^A-Za-z0-9]+", "_");
        collapsed = collapsed.replaceAll("^_+|_+$", "");
        return collapsed.toUpperCase();
    }
}
