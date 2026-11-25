package com.locnguyen.ecommerce.domains.user.service;

public interface CustomerEmailVerificationService {

    void requestVerification();

    void confirmVerification(String otp);
}
