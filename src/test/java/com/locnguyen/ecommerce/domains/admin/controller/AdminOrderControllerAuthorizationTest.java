package com.locnguyen.ecommerce.domains.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class AdminOrderControllerAuthorizationTest {

    @Test
    void orderIdentityResponsesRemainProtectedByTheExistingAdminRoles() {
        PreAuthorize authorization = AdminOrderController.class.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value())
                .isEqualTo("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'STAFF')");
    }
}
