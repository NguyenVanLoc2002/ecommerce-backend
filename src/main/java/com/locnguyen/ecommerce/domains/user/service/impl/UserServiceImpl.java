package com.locnguyen.ecommerce.domains.user.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.customer.repository.CustomerRepository;
import com.locnguyen.ecommerce.domains.user.dto.UpdateProfileRequest;
import com.locnguyen.ecommerce.domains.user.dto.UserProfileResponse;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile() {
        User user = getCurrentUser();
        Customer customer = customerRepository.findByUserIdAndDeletedFalse(user.getId()).orElse(null);
        return buildProfileResponse(user, customer);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(UpdateProfileRequest request) {
        User user = getCurrentUser();

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getPhoneNumber() != null) {
            String phone = request.getPhoneNumber();
            if (!phone.equals(user.getPhoneNumber())
                    && userRepository.existsByPhoneNumber(phone)) {
                throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
            user.setPhoneNumber(phone);
        }

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

    @Override
    public Customer getCurrentCustomer() {
        User user = getCurrentUser();
        return customerRepository.findByUserIdAndDeletedFalse(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

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
                .emailVerified(user.getEmailVerifiedAt() != null)
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
