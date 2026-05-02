package com.locnguyen.ecommerce.domains.admin.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.admin.dto.CreateUserRequest;
import com.locnguyen.ecommerce.domains.admin.dto.UpdateUserRequest;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.auth.dto.UserResponse;
import com.locnguyen.ecommerce.domains.auth.mapper.UserMapper;
import com.locnguyen.ecommerce.domains.user.entity.Role;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.RoleRepository;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserMapper userMapper;
    @Mock AuditLogService auditLogService;

    @InjectMocks AdminUserService adminUserService;

    private static final UUID TARGET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CALLER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CALLER_EMAIL = "caller@example.com";

    @BeforeEach
    void setUpAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CALLER_EMAIL, "n/a", List.of()));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private User userWithRoles(UUID id, RoleName... roleNames) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setEmail("u-" + id + "@example.com");
        u.setStatus(UserStatus.ACTIVE);
        for (RoleName name : roleNames) {
            Role r = new Role();
            r.setName(name);
            u.getRoles().add(r);
        }
        return u;
    }

    private Role role(RoleName name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }

    private void mockCallerLookup(User caller) {
        when(userRepository.findByEmailAndDeletedFalse(CALLER_EMAIL))
                .thenReturn(Optional.of(caller));
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Nested
    class CreateUser {

        @Test
        void rejects_duplicate_email() {
            CreateUserRequest req = new CreateUserRequest();
            req.setEmail("dup@example.com");
            req.setPassword("Passw0rd!");
            req.setFirstName("A");
            req.setRoles(Set.of(RoleName.STAFF));
            when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

            assertThatThrownBy(() -> adminUserService.createUser(req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        @Test
        void rejects_duplicate_phone() {
            CreateUserRequest req = new CreateUserRequest();
            req.setEmail("a@example.com");
            req.setPassword("Passw0rd!");
            req.setFirstName("A");
            req.setPhoneNumber("0912345678");
            req.setRoles(Set.of(RoleName.STAFF));
            when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
            when(userRepository.existsByPhoneNumber("0912345678")).thenReturn(true);

            assertThatThrownBy(() -> adminUserService.createUser(req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        @Test
        void rejects_unknown_role() {
            CreateUserRequest req = new CreateUserRequest();
            req.setEmail("a@example.com");
            req.setPassword("Passw0rd!");
            req.setFirstName("A");
            req.setRoles(Set.of(RoleName.STAFF, RoleName.ADMIN));
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(roleRepository.findByNameIn(any())).thenReturn(Set.of(role(RoleName.STAFF)));

            assertThatThrownBy(() -> adminUserService.createUser(req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
        }

        @Test
        void persists_and_hashes_password() {
            CreateUserRequest req = new CreateUserRequest();
            req.setEmail("New@Example.com");
            req.setPassword("Passw0rd!");
            req.setFirstName(" Loc ");
            req.setRoles(Set.of(RoleName.STAFF));
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(roleRepository.findByNameIn(any())).thenReturn(Set.of(role(RoleName.STAFF)));
            when(passwordEncoder.encode("Passw0rd!")).thenReturn("ENC");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userMapper.toUserResponse(any())).thenReturn(UserResponse.builder().build());

            adminUserService.createUser(req);

            verify(userRepository).save(argThat(u ->
                    "new@example.com".equals(u.getEmail())
                            && "ENC".equals(u.getPasswordHash())
                            && "Loc".equals(u.getFirstName())));
        }
    }

    // ─── Read ───────────────────────────────────────────────────────────────

    @Nested
    class GetUserById {

        @Test
        void throws_when_not_found() {
            when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminUserService.getUserById(TARGET_ID))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    @Nested
    class UpdateUser {

        @Test
        void rejects_empty_roles_set() {
            User target = userWithRoles(TARGET_ID, RoleName.STAFF);
            when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
            mockCallerLookup(userWithRoles(CALLER_ID, RoleName.SUPER_ADMIN));

            UpdateUserRequest req = new UpdateUserRequest();
            req.setRoles(Set.of());

            assertThatThrownBy(() -> adminUserService.updateUser(TARGET_ID, req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        @Test
        void caller_cannot_remove_own_super_admin_role() {
            User self = userWithRoles(CALLER_ID, RoleName.SUPER_ADMIN);
            when(userRepository.findByIdAndDeletedFalse(CALLER_ID)).thenReturn(Optional.of(self));
            mockCallerLookup(self);
            when(roleRepository.findByNameIn(any())).thenReturn(Set.of(role(RoleName.ADMIN)));

            UpdateUserRequest req = new UpdateUserRequest();
            req.setRoles(Set.of(RoleName.ADMIN));

            assertThatThrownBy(() -> adminUserService.updateUser(CALLER_ID, req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        void caller_cannot_deactivate_self() {
            User self = userWithRoles(CALLER_ID, RoleName.ADMIN);
            when(userRepository.findByIdAndDeletedFalse(CALLER_ID)).thenReturn(Optional.of(self));
            mockCallerLookup(self);

            UpdateUserRequest req = new UpdateUserRequest();
            req.setStatus(UserStatus.INACTIVE);

            assertThatThrownBy(() -> adminUserService.updateUser(CALLER_ID, req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        void cannot_remove_last_super_admin_role() {
            User target = userWithRoles(TARGET_ID, RoleName.SUPER_ADMIN);
            User caller = userWithRoles(CALLER_ID, RoleName.SUPER_ADMIN);
            when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
            mockCallerLookup(caller);
            when(roleRepository.findByNameIn(any())).thenReturn(Set.of(role(RoleName.ADMIN)));
            when(userRepository.countActiveByRoleName(RoleName.SUPER_ADMIN)).thenReturn(1L);

            UpdateUserRequest req = new UpdateUserRequest();
            req.setRoles(Set.of(RoleName.ADMIN));

            assertThatThrownBy(() -> adminUserService.updateUser(TARGET_ID, req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        void cannot_deactivate_last_super_admin() {
            User target = userWithRoles(TARGET_ID, RoleName.SUPER_ADMIN);
            User caller = userWithRoles(CALLER_ID, RoleName.SUPER_ADMIN);
            when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
            mockCallerLookup(caller);
            when(userRepository.countActiveByRoleName(RoleName.SUPER_ADMIN)).thenReturn(1L);

            UpdateUserRequest req = new UpdateUserRequest();
            req.setStatus(UserStatus.INACTIVE);

            assertThatThrownBy(() -> adminUserService.updateUser(TARGET_ID, req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        void allows_super_admin_role_removal_when_others_remain() {
            User target = userWithRoles(TARGET_ID, RoleName.SUPER_ADMIN);
            User caller = userWithRoles(CALLER_ID, RoleName.SUPER_ADMIN);
            when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
            mockCallerLookup(caller);
            when(roleRepository.findByNameIn(any())).thenReturn(Set.of(role(RoleName.ADMIN)));
            when(userRepository.countActiveByRoleName(RoleName.SUPER_ADMIN)).thenReturn(2L);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userMapper.toUserResponse(any())).thenReturn(UserResponse.builder().build());

            UpdateUserRequest req = new UpdateUserRequest();
            req.setRoles(Set.of(RoleName.ADMIN));

            adminUserService.updateUser(TARGET_ID, req);

            verify(userRepository).save(any(User.class));
        }

        @Test
        void rejects_phone_already_used_by_other_user() {
            User target = userWithRoles(TARGET_ID, RoleName.STAFF);
            target.setPhoneNumber("0900000001");
            when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
            mockCallerLookup(userWithRoles(CALLER_ID, RoleName.ADMIN));
            when(userRepository.existsByPhoneNumber("0911111111")).thenReturn(true);

            UpdateUserRequest req = new UpdateUserRequest();
            req.setPhoneNumber("0911111111");

            assertThatThrownBy(() -> adminUserService.updateUser(TARGET_ID, req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PHONE_ALREADY_EXISTS);
        }
    }

    // ─── Delete ─────────────────────────────────────────────────────────────

    @Nested
    class DeleteUser {

        @Test
        void cannot_delete_self() {
            User self = userWithRoles(CALLER_ID, RoleName.ADMIN);
            when(userRepository.findByIdAndDeletedFalse(CALLER_ID)).thenReturn(Optional.of(self));
            mockCallerLookup(self);

            assertThatThrownBy(() -> adminUserService.deleteUser(CALLER_ID))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        void cannot_delete_last_super_admin() {
            User target = userWithRoles(TARGET_ID, RoleName.SUPER_ADMIN);
            User caller = userWithRoles(CALLER_ID, RoleName.SUPER_ADMIN);
            when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
            mockCallerLookup(caller);
            when(userRepository.countActiveByRoleName(RoleName.SUPER_ADMIN)).thenReturn(1L);

            assertThatThrownBy(() -> adminUserService.deleteUser(TARGET_ID))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        void soft_deletes_and_sets_status_inactive() {
            User target = userWithRoles(TARGET_ID, RoleName.STAFF);
            User caller = userWithRoles(CALLER_ID, RoleName.ADMIN);
            when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
            mockCallerLookup(caller);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            adminUserService.deleteUser(TARGET_ID);

            assertThat(target.isDeleted()).isTrue();
            assertThat(target.getStatus()).isEqualTo(UserStatus.INACTIVE);
            verify(userRepository).save(target);
        }

        @Test
        void allows_deleting_super_admin_when_others_remain() {
            User target = userWithRoles(TARGET_ID, RoleName.SUPER_ADMIN);
            User caller = userWithRoles(CALLER_ID, RoleName.SUPER_ADMIN);
            when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
            mockCallerLookup(caller);
            when(userRepository.countActiveByRoleName(RoleName.SUPER_ADMIN)).thenReturn(2L);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            adminUserService.deleteUser(TARGET_ID);

            assertThat(target.isDeleted()).isTrue();
            assertThat(target.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }
    }
}
