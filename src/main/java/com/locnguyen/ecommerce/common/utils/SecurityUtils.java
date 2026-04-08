package com.locnguyen.ecommerce.common.utils;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Utility for accessing the current security context.
 * All methods are static — intended for use in service and utility layers
 * where Spring injection is unavailable or inconvenient.
 */
public final class SecurityUtils {

    private static final String ANONYMOUS_USER = "anonymousUser";

    private SecurityUtils() {}

    /**
     * Returns the username of the currently authenticated user,
     * or empty if the request is anonymous or unauthenticated.
     */
    public static Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null
                || !auth.isAuthenticated()
                || ANONYMOUS_USER.equals(auth.getPrincipal())) {
            return Optional.empty();
        }

        return Optional.of(auth.getName());
    }

    /**
     * Returns the current username, or "system" for anonymous/unauthenticated requests.
     * Useful for audit fields when acting on behalf of the system.
     */
    public static String getCurrentUsernameOrSystem() {
        return getCurrentUsername().orElse(AppConstants.SYSTEM_USER);
    }

    /**
     * Returns true if the current request is from an authenticated user
     * (not anonymous and not unauthenticated).
     */
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !ANONYMOUS_USER.equals(auth.getPrincipal());
    }

    /**
     * Returns true if the current user has the specified role.
     * The role name should NOT include the "ROLE_" prefix.
     *
     * <p>Example: {@code SecurityUtils.hasRole("ADMIN")}
     */
    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;

        String prefixed = "ROLE_" + role;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(prefixed::equals);
    }

    /**
     * Returns true if the current user has any of the specified roles.
     */
    public static boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRole(role)) return true;
        }
        return false;
    }
}
