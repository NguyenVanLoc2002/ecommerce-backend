package com.locnguyen.ecommerce.domains.verification.service;

import com.locnguyen.ecommerce.domains.verification.entity.VerificationToken;
import com.locnguyen.ecommerce.domains.verification.enums.VerificationPurpose;

/**
 * Coordinates OTP issuance, verification, and reset-token lifecycle.
 */
public interface OtpService {

    /**
     * Generate a fresh OTP for the given (purpose, target), supersede any
     * previous active tokens, persist a SHA-256 hash, and dispatch the email.
     *
     * @return the newly created (and persisted) verification token row
     */
    VerificationToken generateAndSendOtp(String target, VerificationPurpose purpose);

    /**
     * Verify a raw OTP against the active token for (purpose, target).
     * Increments {@code attempt_count} on every call. Marks {@code verified_at}
     * on success.
     *
     * @return the verified token row (still present in DB for the reset-token issuance step)
     */
    VerificationToken verifyOtp(String target, VerificationPurpose purpose, String rawOtp);

    VerificationToken verifyEmailOtp(String target, String rawOtp);

    /**
     * Issue a one-shot opaque reset token for an already-verified token row.
     * Returns the raw reset-token value to send to the client; the row stores
     * only its SHA-256 hash.
     */
    IssuedResetToken issueResetToken(VerificationToken verifiedToken);

    /**
     * Validate a raw reset token returned by {@link #issueResetToken}.
     * Returns the verification token row if valid; throws otherwise.
     */
    VerificationToken validateResetToken(String rawResetToken);

    /**
     * Mark a verification token as fully consumed (used).
     * Called by callers after they have completed the protected operation.
     */
    void markUsed(VerificationToken token);

    record IssuedResetToken(String rawResetToken, java.time.Instant expiresAt) {
    }
}
