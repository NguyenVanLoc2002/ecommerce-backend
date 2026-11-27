package com.locnguyen.ecommerce.domains.shippingpolicy;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.domains.user.entity.Role;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShippingPolicyAccessTest {
    private final UUID id = UUID.randomUUID();
    private final UserRepository users = mock(UserRepository.class);
    private final User user = mock(User.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyExplicitlyGrantedActiveAdminsCanEditOrPublish() {
        var access = new ShippingPolicyAccess(new ShippingPolicyAccess.Settings(true, Set.of(id), Set.of()), users);
        var role = mock(Role.class);
        when(role.getName()).thenReturn(RoleName.ADMIN);
        when(users.findByEmailAndDeletedFalse("test@example.invalid")).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getRoles()).thenReturn(Set.of(role));
        when(user.getId()).thenReturn(id);
        authenticate("ROLE_ADMIN");

        assertThat(access.requireEdit()).isEqualTo(id);
        assertThat(access.capabilities().canPublish()).isFalse();
        assertThatThrownBy(access::requirePublish).isInstanceOf(AppException.class);

        authenticate("ROLE_CUSTOMER");
        assertThatThrownBy(access::requireEdit).isInstanceOf(AppException.class);
        authenticate("ROLE_ADMIN");
        when(user.getStatus()).thenReturn(UserStatus.LOCKED);
        assertThatThrownBy(access::requireEdit).isInstanceOf(AppException.class);
    }

    private static void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test@example.invalid", null, List.of(new SimpleGrantedAuthority(authority))));
    }
}
