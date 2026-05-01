package com.locnguyen.ecommerce.domains.admin.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.admin.dto.AdminUserFilter;
import com.locnguyen.ecommerce.domains.admin.dto.CreateUserRequest;
import com.locnguyen.ecommerce.domains.admin.dto.UpdateUserRequest;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.auth.dto.UserResponse;
import com.locnguyen.ecommerce.domains.auth.mapper.UserMapper;
import com.locnguyen.ecommerce.domains.user.entity.Role;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.RoleRepository;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import com.locnguyen.ecommerce.domains.user.specification.UserSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin-only user management service.
 *
 * <p>Separated from {@link com.locnguyen.ecommerce.domains.auth.service.AuthService} because
 * role assignment is an admin concern — the public register API always assigns CUSTOMER.
 * Admin APIs can assign STAFF, ADMIN, or SUPER_ADMIN without going through the auth flow.
 *
 * <p>Scope: this service deals only with system users (STAFF/ADMIN/SUPER_ADMIN). CUSTOMER
 * accounts are managed via auth/customer endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    // ─── Create ─────────────────────────────────────────────────────────────

    /**
     * Create a system user with explicit role assignment.
     *
     * <ol>
     *   <li>Validate email and phone uniqueness</li>
     *   <li>Resolve all requested roles from the database</li>
     *   <li>Hash password and persist user</li>
     * </ol>
     *
     * <p>Note: no Customer profile is created here — admin/staff accounts don't need one.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (request.getPhoneNumber() != null
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        Set<Role> resolvedRoles = resolveRoles(request.getRoles());

        User user = new User();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName() != null ? request.getLastName().trim() : null);
        user.setPhoneNumber(request.getPhoneNumber());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(resolvedRoles);

        user = userRepository.save(user);

        log.info("Admin created user: id={} email={} roles={}",
                user.getId(), user.getEmail(), request.getRoles());
        auditLogService.log(AuditAction.USER_CREATED, "USER",
                String.valueOf(user.getId()),
                "email=" + user.getEmail() + " roles=" + request.getRoles());

        return userMapper.toUserResponse(user);
    }

    // ─── Read ───────────────────────────────────────────────────────────────

    /** Paginated, filterable system user list for admin. */
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getUsers(AdminUserFilter filter, Pageable pageable) {
        return PagedResponse.of(
                userRepository.findAll(UserSpecification.withFilter(filter), pageable)
                        .map(userMapper::toUserResponse)
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return userMapper.toUserResponse(findOrThrow(id));
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    /**
     * Partially update a system user. Only provided fields are applied.
     * Password and email are intentionally not updatable here.
     */
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        UUID currentUserId = resolveCurrentUserId();
        boolean isSelf = currentUserId != null && currentUserId.equals(user.getId());

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getPhoneNumber() != null) {
            String newPhone = request.getPhoneNumber().trim();
            if (!newPhone.equals(user.getPhoneNumber())
                    && userRepository.existsByPhoneNumber(newPhone)) {
                throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
            user.setPhoneNumber(newPhone);
        }

        if (request.getRoles() != null) {
            if (request.getRoles().isEmpty()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Roles must not be empty when provided");
            }
            Set<Role> newRoles = resolveRoles(request.getRoles());

            if (isSelf) {
                Set<RoleName> currentRoleNames = roleNames(user.getRoles());
                Set<RoleName> newRoleNames = request.getRoles();
                if (currentRoleNames.contains(RoleName.SUPER_ADMIN)
                        && !newRoleNames.contains(RoleName.SUPER_ADMIN)) {
                    throw new AppException(ErrorCode.FORBIDDEN,
                            "You cannot remove your own SUPER_ADMIN role");
                }
                if (currentRoleNames.contains(RoleName.ADMIN)
                        && !newRoleNames.contains(RoleName.ADMIN)
                        && !newRoleNames.contains(RoleName.SUPER_ADMIN)) {
                    throw new AppException(ErrorCode.FORBIDDEN,
                            "You cannot remove your own admin privileges — would lock you out");
                }
            }

            if (roleNames(user.getRoles()).contains(RoleName.SUPER_ADMIN)
                    && !request.getRoles().contains(RoleName.SUPER_ADMIN)) {
                guardLastSuperAdmin(user);
            }

            user.setRoles(newRoles);
        }

        if (request.getStatus() != null && request.getStatus() != user.getStatus()) {
            if (isSelf && request.getStatus() != UserStatus.ACTIVE) {
                throw new AppException(ErrorCode.FORBIDDEN,
                        "You cannot deactivate or lock your own account");
            }
            if (request.getStatus() != UserStatus.ACTIVE
                    && roleNames(user.getRoles()).contains(RoleName.SUPER_ADMIN)) {
                guardLastSuperAdmin(user);
            }
            user.setStatus(request.getStatus());
        }

        user = userRepository.save(user);

        log.info("Admin updated user: id={} by={}", id, SecurityUtils.getCurrentUsernameOrSystem());
        auditLogService.log(AuditAction.USER_UPDATED, "USER", String.valueOf(id));

        return userMapper.toUserResponse(user);
    }

    // ─── Delete / deactivate ────────────────────────────────────────────────

    /**
     * Soft-delete a system user. Deleted users cannot log in (filtered by SQLRestriction
     * on User), and their access tokens become invalid on next request because
     * {@code findByEmailAndDeletedFalse} returns empty.
     */
    @Transactional
    public void deleteUser(UUID id) {
        User user = findOrThrow(id);

        UUID currentUserId = resolveCurrentUserId();
        if (currentUserId != null && currentUserId.equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "You cannot delete your own account");
        }

        if (roleNames(user.getRoles()).contains(RoleName.SUPER_ADMIN)) {
            guardLastSuperAdmin(user);
        }

        String actor = SecurityUtils.getCurrentUsernameOrSystem();
        user.setStatus(UserStatus.INACTIVE);
        user.softDelete(actor);
        userRepository.save(user);

        log.info("Admin deleted user: id={} by={}", id, actor);
        auditLogService.log(AuditAction.USER_DISABLED, "USER", String.valueOf(id),
                "soft delete by admin");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private User findOrThrow(UUID id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private Set<Role> resolveRoles(Set<RoleName> requestedRoles) {
        Set<Role> resolved = roleRepository.findByNameIn(requestedRoles);
        if (resolved.size() != requestedRoles.size()) {
            Set<RoleName> foundNames = resolved.stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet());
            Set<RoleName> missing = requestedRoles.stream()
                    .filter(name -> !foundNames.contains(name))
                    .collect(Collectors.toSet());
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Unknown roles: " + missing + " — check Flyway seed data");
        }
        return resolved;
    }

    private Set<RoleName> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).collect(Collectors.toSet());
    }

    private void guardLastSuperAdmin(User affected) {
        long activeSuperAdmins = userRepository.countActiveByRoleName(RoleName.SUPER_ADMIN);
        boolean affectedIsActiveSuperAdmin = !affected.isDeleted()
                && affected.getStatus() == UserStatus.ACTIVE
                && roleNames(affected.getRoles()).contains(RoleName.SUPER_ADMIN);
        long remaining = affectedIsActiveSuperAdmin ? activeSuperAdmins - 1 : activeSuperAdmins;
        if (remaining <= 0) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Cannot remove the last active SUPER_ADMIN");
        }
    }

    private UUID resolveCurrentUserId() {
        return SecurityUtils.getCurrentUsername()
                .flatMap(userRepository::findByEmailAndDeletedFalse)
                .map(User::getId)
                .orElse(null);
    }
}
