package com.locnguyen.ecommerce.domains.returns.enums;

import java.util.Objects;
import java.util.stream.Stream;

public enum ReturnPolicy {
    STANDARD,
    SEALED_ONLY,
    REVIEW_REQUIRED,
    CUSTOMIZED_FINAL_SALE;

    public static ReturnPolicy resolve(ReturnPolicy productPolicy, Stream<ReturnPolicy> categoryPolicies) {
        return Stream.concat(Stream.of(productPolicy), categoryPolicies)
                .filter(Objects::nonNull).max(ReturnPolicy::compareTo).orElse(REVIEW_REQUIRED);
    }

    public boolean allowsDiscretionaryReturn(boolean unopened) {
        return this == STANDARD || (this == SEALED_ONLY && unopened);
    }
}
