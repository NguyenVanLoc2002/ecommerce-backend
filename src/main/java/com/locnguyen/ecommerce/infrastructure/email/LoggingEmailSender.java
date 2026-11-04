package com.locnguyen.ecommerce.infrastructure.email;

import com.locnguyen.ecommerce.domains.verification.enums.VerificationPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Default {@link EmailSender} for environments without an SMTP provider.
 *
 * <p>Records that an OTP email <em>would have been sent</em>; never logs the
 * raw OTP. It is active unless SMTP delivery is explicitly enabled.
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(prefix = "app.email.smtp", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendOtpEmail(String toEmail, String otp, VerificationPurpose purpose, int expiresMinutes) {
        // Never log the raw OTP — this is the safe default for the dev profile.
        log.info("[EMAIL-DEV] OTP email would be sent to: {} for purpose: {} (expires in {} min)",
                toEmail, purpose, expiresMinutes);
    }
}
