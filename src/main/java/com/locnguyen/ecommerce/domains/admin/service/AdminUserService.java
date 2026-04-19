package com.locnguyen.ecommerce.domains.admin.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.admin.dto.CreateUserRequest;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Admin-only user management service.
 *
 * <p>Separated from {@link com.locnguyen.ecommerce.domains.auth.service.AuthService} because
 * role assignment is an admin concern — the public register API always assigns CUSTOMER.
 * Admin APIs can assign STAFF, ADMIN, or SUPER_ADMIN without going through the auth flow.
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

        Set<RoleName> requestedRoles = request.getRoles();
        Set<Role> resolvedRoles = roleRepository.findByNameIn(requestedRoles);

        if (resolvedRoles.size() != requestedRoles.size()) {
            Set<RoleName> foundNames = resolvedRoles.stream()
                    .map(Role::getName)
                    .collect(java.util.stream.Collectors.toSet());
            Set<RoleName> missing = requestedRoles.stream()
                    .filter(name -> !foundNames.contains(name))
                    .collect(java.util.stream.Collectors.toSet());
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Unknown roles: " + missing + " — check Flyway seed data");
        }

        User user = new User();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName() != null ? request.getLastName().trim() : null);
        user.setPhoneNumber(request.getPhoneNumber());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(resolvedRoles);

        user = userRepository.save(user);

        log.info("Admin created user: id={} email={} roles={}", user.getId(), user.getEmail(), requestedRoles);
        auditLogService.log(AuditAction.USER_CREATED, "USER",
                String.valueOf(user.getId()), "email=" + user.getEmail() + " roles=" + requestedRoles);

        return userMapper.toUserResponse(user);
    }
}
