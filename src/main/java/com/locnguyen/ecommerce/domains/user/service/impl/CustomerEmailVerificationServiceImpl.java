package com.locnguyen.ecommerce.domains.user.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import com.locnguyen.ecommerce.domains.user.service.CustomerEmailVerificationService;
import com.locnguyen.ecommerce.domains.verification.entity.VerificationToken;
import com.locnguyen.ecommerce.domains.verification.enums.VerificationPurpose;
import com.locnguyen.ecommerce.domains.verification.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerEmailVerificationServiceImpl implements CustomerEmailVerificationService {

    private final UserRepository userRepository;
    private final OtpService otpService;

    @Override
    @Transactional
    public void requestVerification() {
        User user = lockCurrentUser();
        if (user.getEmailVerifiedAt() == null) {
            otpService.generateAndSendOtp(user.getEmail(), VerificationPurpose.VERIFY_EMAIL);
        }
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public void confirmVerification(String otp) {
        User user = lockCurrentUser();
        if (user.getEmailVerifiedAt() != null) {
            return;
        }

        // Failed OTP attempts are persisted even when the request returns an application error.
        VerificationToken token = otpService.verifyEmailOtp(user.getEmail(), otp);
        if (token.getUser() == null || !user.getId().equals(token.getUser().getId())) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }
        user.setEmailVerifiedAt(token.getVerifiedAt());
        otpService.markUsed(token);
    }

    private User lockCurrentUser() {
        String email = SecurityUtils.getCurrentUsername().orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        User user = userRepository.findCurrentUserForEmailVerification(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }
}
