package com.locnguyen.ecommerce.domains.user.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import com.locnguyen.ecommerce.domains.user.service.impl.CustomerEmailVerificationServiceImpl;
import com.locnguyen.ecommerce.domains.verification.entity.VerificationToken;
import com.locnguyen.ecommerce.domains.verification.enums.VerificationPurpose;
import com.locnguyen.ecommerce.domains.verification.service.OtpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerEmailVerificationServiceTest {

    private static final String EMAIL = "customer@example.test";
    private final User user = new User();

    @Mock UserRepository userRepository;
    @Mock OtpService otpService;
    @InjectMocks CustomerEmailVerificationServiceImpl service;

    @BeforeEach
    void authenticateCustomer() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                EMAIL, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail(EMAIL);
        when(userRepository.findCurrentUserForEmailVerification(EMAIL)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendsOnlyToAuthenticatedEmailWithDedicatedPurpose() {
        service.requestVerification();
        verify(otpService).generateAndSendOtp(EMAIL, VerificationPurpose.VERIFY_EMAIL);
    }

    @Test
    void consumesSuccessfulOtpAndPersistsVerificationTimestampOnCurrentUser() {
        LocalDateTime verifiedAt = LocalDateTime.now();
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setVerifiedAt(verifiedAt);
        when(otpService.verifyEmailOtp(EMAIL, "123456")).thenReturn(token);

        service.confirmVerification("123456");

        assertThat(user.getEmailVerifiedAt()).isEqualTo(verifiedAt);
        verify(otpService).markUsed(token);
    }

    @Test
    void refusesOtpLinkedToAnotherAccount() {
        User differentUser = new User();
        ReflectionTestUtils.setField(differentUser, "id", UUID.randomUUID());
        VerificationToken token = new VerificationToken();
        token.setUser(differentUser);
        when(otpService.verifyEmailOtp(EMAIL, "123456")).thenReturn(token);

        assertThatThrownBy(() -> service.confirmVerification("123456"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.OTP_INVALID));
        assertThat(user.getEmailVerifiedAt()).isNull();
        verify(otpService, never()).markUsed(token);
    }
}
