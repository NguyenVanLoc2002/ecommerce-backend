package com.locnguyen.ecommerce.domains.user.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.customer.repository.CustomerRepository;
import com.locnguyen.ecommerce.domains.user.dto.UpdateProfileRequest;
import com.locnguyen.ecommerce.domains.user.dto.UserProfileResponse;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * User profile service — handles GET /me and PATCH /me.
 *
 * <p>Combines data from User (auth identity) and Customer (business profile)
 * into a single {@link UserProfileResponse}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    // ─── Get profile ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        User user = getCurrentUser();
        Customer customer = customerRepository.findByUserIdAndDeletedFalse(user.getId()).orElse(null);
        return buildProfileResponse(user, customer);
    }

    // ─── Update profile ──────────────────────────────────────────────────────

    /**
     * Updates user profile fields.
     *
     * <p>Strategy: only non-null fields in the request are applied (partial update).
     * Phone uniqueness is checked only when the phone field is actually provided.
     *
     * <p>Customer record is lazily created if it doesn't exist yet (shouldn't happen
     * after registration, but defensive programming).
     */
    @Transactional
    public UserProfileResponse updateMyProfile(UpdateProfileRequest request) {
        User user = getCurrentUser();

        // ── Update User fields ───────────────────────────────────────────────
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getPhoneNumber() != null) {
            String phone = request.getPhoneNumber();
            // Check uniqueness only if changing to a different value
            if (!phone.equals(user.getPhoneNumber())
                    && userRepository.existsByPhoneNumber(phone)) {
                throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
            user.setPhoneNumber(phone);
        }

        // ── Update or create Customer fields ─────────────────────────────────
        Customer customer = customerRepository.findByUserIdAndDeletedFalse(user.getId())
                .orElseGet(() -> {
                    Customer c = new Customer(user);
                    log.info("Lazily created customer profile for userId={}", user.getId());
                    return c;
                });

        if (request.getGender() != null) {
            customer.setGender(request.getGender());
        }
        if (request.getBirthDate() != null) {
            customer.setBirthDate(request.getBirthDate());
        }

        userRepository.save(user);
        customerRepository.save(customer);

        log.info("Profile updated: userId={}", user.getId());
        return buildProfileResponse(user, customer);
    }

    // ─── Customer resolution ─────────────────────────────────────────────────

    /**
     * Returns the Customer for the current authenticated user.
     * Throws if user has no customer record (shouldn't happen after registration).
     */
    public Customer getCurrentCustomer() {
        User user = getCurrentUser();
        return customerRepository.findByUserIdAndDeletedFalse(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private User getCurrentUser() {
        String email = SecurityUtils.getCurrentUsername()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        return userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private UserProfileResponse buildProfileResponse(User user, Customer customer) {
        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .status(user.getStatus())
                .roles(user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt());

        if (customer != null) {
            builder.customerId(customer.getId())
                    .gender(customer.getGender())
                    .birthDate(customer.getBirthDate())
                    .avatarUrl(customer.getAvatarUrl())
                    .loyaltyPoints(customer.getLoyaltyPoints());
        }

        return builder.build();
    }
}
