package com.locnguyen.ecommerce.domains.verification.service.impl;

import com.locnguyen.ecommerce.common.config.AppProperties;
import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import com.locnguyen.ecommerce.domains.verification.entity.VerificationToken;
import com.locnguyen.ecommerce.domains.verification.enums.VerificationPurpose;
import com.locnguyen.ecommerce.domains.verification.enums.VerificationTargetType;
import com.locnguyen.ecommerce.domains.verification.repository.VerificationTokenRepository;
import com.locnguyen.ecommerce.domains.verification.service.OtpRateLimiter;
import com.locnguyen.ecommerce.domains.verification.service.OtpService;
import com.locnguyen.ecommerce.infrastructure.email.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link OtpService} implementation.
 *
 * <p>OTP storage rule: the raw OTP is never persisted. Only
 * {@code SHA-256(purpose + ":" + target + ":" + rawOtp + ":" + pepper)} is stored,
 * where {@code pepper} is the JWT secret (re-used as a server-side secret).
 *
 * <p>Reset tokens are random 256-bit URL-safe values; only their SHA-256 hash
 * is persisted on the same {@link VerificationToken} row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;

    private final VerificationTokenRepository verificationTokenRepository;
    private final UserRepository userRepository;
    private final OtpRateLimiter otpRateLimiter;
    private final EmailSender emailSender;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public VerificationToken generateAndSendOtp(String target, VerificationPurpose purpose) {
        String normalizedTarget = normalizeEmail(target);
        otpRateLimiter.checkAndRecordSend(purpose, normalizedTarget);

        LocalDateTime now = LocalDateTime.now();
        verificationTokenRepository.markPreviousSuperseded(normalizedTarget, purpose, now);

        String rawOtp = generateNumericOtp();
        AppProperties.Otp otpConfig = appProperties.getOtp();

        VerificationToken token = new VerificationToken();
        token.setTargetType(VerificationTargetType.EMAIL);
        token.setTarget(normalizedTarget);
        token.setPurpose(purpose);
        token.setTokenHash(hashOtp(purpose, normalizedTarget, rawOtp));
        token.setExpiresAt(now.plusMinutes(otpConfig.getExpiresMinutes()));
        token.setMaxAttempts(otpConfig.getMaxAttempts());
        token.setLastSentAt(now);
        token.setResendCount(0);
        token.setUser(findUserByEmailIfPresent(normalizedTarget));

        VerificationToken saved = verificationTokenRepository.save(token);

        // Never log the raw OTP. Only metadata.
        log.info("OTP issued: purpose={} target={} expiresAt={}",
                purpose, normalizedTarget, saved.getExpiresAt());

        emailSender.sendOtpEmail(normalizedTarget, rawOtp, purpose, otpConfig.getExpiresMinutes());
        return saved;
    }

    @Override
    @Transactional
    public VerificationToken verifyOtp(String target, VerificationPurpose purpose, String rawOtp) {
        return verify(target, purpose, rawOtp);
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public VerificationToken verifyEmailOtp(String target, String rawOtp) {
        return verify(target, VerificationPurpose.VERIFY_EMAIL, rawOtp);
    }

    private VerificationToken verify(String target, VerificationPurpose purpose, String rawOtp) {
        String normalizedTarget = normalizeEmail(target);
        otpRateLimiter.recordVerifyAttempt(purpose, normalizedTarget);

        LocalDateTime now = LocalDateTime.now();
        List<VerificationToken> active =
                verificationTokenRepository.findActiveByTargetAndPurpose(normalizedTarget, purpose, now);
        if (active.isEmpty()) {
            log.warn("OTP verify failed (no active token): purpose={} target={}", purpose, normalizedTarget);
            throw new AppException(ErrorCode.OTP_INVALID);
        }
        VerificationToken token = active.get(0);

        if (token.isUsed()) {
            log.warn("OTP verify failed (already used): purpose={} target={}", purpose, normalizedTarget);
            throw new AppException(ErrorCode.OTP_USED);
        }
        if (token.isExpired()) {
            log.warn("OTP verify failed (expired): purpose={} target={}", purpose, normalizedTarget);
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }
        if (token.hasReachedMaxAttempts()) {
            log.warn("OTP verify failed (max attempts): purpose={} target={}", purpose, normalizedTarget);
            throw new AppException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
        }

        token.setAttemptCount(token.getAttemptCount() + 1);

        String submittedHash = hashOtp(purpose, normalizedTarget, rawOtp);
        if (!constantTimeEquals(submittedHash, token.getTokenHash())) {
            verificationTokenRepository.save(token);
            if (token.hasReachedMaxAttempts()) {
                log.warn("OTP verify failed (max attempts on this submission): purpose={} target={}",
                        purpose, normalizedTarget);
                throw new AppException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
            }
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        token.setVerifiedAt(LocalDateTime.now());
        VerificationToken saved = verificationTokenRepository.save(token);
        log.info("OTP verified: purpose={} target={}", purpose, normalizedTarget);
        return saved;
    }

    @Override
    @Transactional
    public IssuedResetToken issueResetToken(VerificationToken verifiedToken) {
        if (!verifiedToken.isVerified()) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }
        if (verifiedToken.isUsed()) {
            throw new AppException(ErrorCode.OTP_USED);
        }

        String rawResetToken = generateOpaqueResetToken();
        verifiedToken.setResetTokenHash(hashResetToken(rawResetToken));
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusMinutes(appProperties.getResetToken().getExpiresMinutes());
        verifiedToken.setExpiresAt(expiresAt);

        verificationTokenRepository.save(verifiedToken);

        log.info("Reset token issued: purpose={} target={} expiresAt={}",
                verifiedToken.getPurpose(), verifiedToken.getTarget(), expiresAt);

        return new IssuedResetToken(rawResetToken, expiresAt.toInstant(ZoneOffset.UTC));
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationToken validateResetToken(String rawResetToken) {
        if (rawResetToken == null || rawResetToken.isBlank()) {
            throw new AppException(ErrorCode.RESET_TOKEN_INVALID);
        }
        VerificationToken token = verificationTokenRepository
                .findByResetTokenHash(hashResetToken(rawResetToken))
                .orElseThrow(() -> new AppException(ErrorCode.RESET_TOKEN_INVALID));

        if (token.isUsed()) {
            throw new AppException(ErrorCode.RESET_TOKEN_INVALID);
        }
        if (token.isExpired()) {
            throw new AppException(ErrorCode.RESET_TOKEN_EXPIRED);
        }
        if (!token.isVerified()) {
            throw new AppException(ErrorCode.RESET_TOKEN_INVALID);
        }
        return token;
    }

    @Override
    @Transactional
    public void markUsed(VerificationToken token) {
        token.setUsedAt(LocalDateTime.now());
        verificationTokenRepository.save(token);
    }

    // ─── Internal ───────────────────────────────────────────────────────────

    private String generateNumericOtp() {
        StringBuilder builder = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    private String generateOpaqueResetToken() {
        // 256 bits of entropy, encoded as a UUID-shaped string we can transport over JSON.
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String hashOtp(VerificationPurpose purpose, String target, String rawOtp) {
        String pepper = appProperties.getJwt().getSecret();
        String input = purpose.name() + ":" + target + ":" + rawOtp + ":" + pepper;
        return sha256Hex(input);
    }

    private String hashResetToken(String rawResetToken) {
        // Reset token is high-entropy already; pepper not strictly required but cheap.
        String pepper = appProperties.getJwt().getSecret();
        return sha256Hex(rawResetToken + ":" + pepper);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private User findUserByEmailIfPresent(String email) {
        return userRepository.findByEmailAndDeletedFalse(email).orElse(null);
    }

    /** Exposed only for {@link Instant} support — unused at the moment. */
    @SuppressWarnings("unused")
    private static Instant nowInstant() {
        return Instant.now();
    }
}
