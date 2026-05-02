package com.locnguyen.ecommerce.domains.admin.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.admin.dto.AdminCustomerFilter;
import com.locnguyen.ecommerce.domains.admin.dto.AdminCustomerResponse;
import com.locnguyen.ecommerce.domains.admin.dto.UpdateCustomerRequest;
import com.locnguyen.ecommerce.domains.admin.dto.UpdateCustomerStatusRequest;
import com.locnguyen.ecommerce.domains.auditlog.enums.AuditAction;
import com.locnguyen.ecommerce.domains.auditlog.service.AuditLogService;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.customer.repository.CustomerRepository;
import com.locnguyen.ecommerce.domains.customer.specification.CustomerSpecification;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Admin-only customer management service.
 *
 * <p>Scope: customer business profiles only. System users (STAFF/ADMIN/SUPER_ADMIN)
 * are managed under {@code /api/v1/admin/users} (see {@link AdminUserService}).
 *
 * <p>The service operates on the {@link Customer} entity joined with its linked
 * {@link User}. Soft delete is enforced — historical orders, reviews, payments,
 * invoices, shipments and audit data continue to reference the customer/user rows
 * (which remain intact in the database, just hidden by {@code @SQLRestriction}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    // ─── Read ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<AdminCustomerResponse> getCustomers(AdminCustomerFilter filter,
                                                             Pageable pageable) {
        return PagedResponse.of(
                customerRepository.findAll(CustomerSpecification.withFilter(filter), pageable)
                        .map(this::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public AdminCustomerResponse getCustomerById(UUID id) {
        return toResponse(findCustomerOrThrow(id));
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    @Transactional
    public AdminCustomerResponse updateCustomer(UUID id, UpdateCustomerRequest request) {
        Customer customer = findCustomerOrThrow(id);
        User user = customer.getUser();
        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        boolean userChanged = false;

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().trim());
            userChanged = true;
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
            userChanged = true;
        }
        if (request.getPhoneNumber() != null) {
            String newPhone = request.getPhoneNumber().trim();
            if (!newPhone.equals(user.getPhoneNumber())
                    && userRepository.existsByPhoneNumber(newPhone)) {
                throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
            user.setPhoneNumber(newPhone);
            userChanged = true;
        }

        if (request.getGender() != null) {
            customer.setGender(request.getGender());
        }
        if (request.getBirthDate() != null) {
            customer.setBirthDate(request.getBirthDate());
        }
        if (request.getAvatarUrl() != null) {
            String trimmed = request.getAvatarUrl().trim();
            customer.setAvatarUrl(trimmed.isEmpty() ? null : trimmed);
        }

        if (userChanged) {
            userRepository.save(user);
        }
        customer = customerRepository.save(customer);

        log.info("Admin updated customer: id={} by={}",
                id, SecurityUtils.getCurrentUsernameOrSystem());
        auditLogService.log(AuditAction.CUSTOMER_UPDATED, "CUSTOMER", String.valueOf(id));

        return toResponse(customer);
    }

    @Transactional
    public AdminCustomerResponse updateCustomerStatus(UUID id, UpdateCustomerStatusRequest request) {
        Customer customer = findCustomerOrThrow(id);
        User user = customer.getUser();
        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        UserStatus newStatus = request.getStatus();
        if (newStatus != user.getStatus()) {
            user.setStatus(newStatus);
            userRepository.save(user);
        }

        log.info("Admin changed customer status: id={} -> {} by={}",
                id, newStatus, SecurityUtils.getCurrentUsernameOrSystem());
        auditLogService.log(AuditAction.CUSTOMER_STATUS_CHANGED, "CUSTOMER",
                String.valueOf(id), "status=" + newStatus);

        return toResponse(customer);
    }

    // ─── Delete ─────────────────────────────────────────────────────────────

    /**
     * Soft-delete a customer profile and the linked user account.
     *
     * <p>Historical references (orders, reviews, payments, invoices, shipments,
     * audit logs) are preserved at the database level — only the {@code Customer}
     * and {@code User} rows are flagged as deleted (and the user account is forced
     * to {@code INACTIVE} so it cannot authenticate). No hard delete is ever issued.
     */
    @Transactional
    public void deleteCustomer(UUID id) {
        Customer customer = findCustomerOrThrow(id);
        User user = customer.getUser();
        String actor = SecurityUtils.getCurrentUsernameOrSystem();

        if (user != null) {
            user.setStatus(UserStatus.INACTIVE);
            user.softDelete(actor);
            userRepository.save(user);
        }

        customer.softDelete(actor);
        customerRepository.save(customer);

        log.info("Admin soft-deleted customer: id={} by={}", id, actor);
        auditLogService.log(AuditAction.CUSTOMER_DELETED, "CUSTOMER", String.valueOf(id),
                "soft delete by admin");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Customer findCustomerOrThrow(UUID id) {
        return customerRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    private AdminCustomerResponse toResponse(Customer customer) {
        User user = customer.getUser();
        AdminCustomerResponse.AdminCustomerResponseBuilder builder = AdminCustomerResponse.builder()
                .id(customer.getId())
                .gender(customer.getGender())
                .birthDate(customer.getBirthDate())
                .avatarUrl(customer.getAvatarUrl())
                .loyaltyPoints(customer.getLoyaltyPoints())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt());

        if (user != null) {
            builder.userId(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .phoneNumber(user.getPhoneNumber())
                    .status(user.getStatus());
        }

        return builder.build();
    }
}
