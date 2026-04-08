package com.locnguyen.ecommerce.common.auditing;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Provides the current auditor (username) for JPA audit fields.
 * Falls back to "system" when no authenticated user is present.
 */
public class AuditorAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM_AUDITOR = "system";
    private static final String ANONYMOUS_USER = "anonymousUser";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || ANONYMOUS_USER.equals(authentication.getPrincipal())) {
            return Optional.of(SYSTEM_AUDITOR);
        }

        return Optional.of(authentication.getName());
    }
}
