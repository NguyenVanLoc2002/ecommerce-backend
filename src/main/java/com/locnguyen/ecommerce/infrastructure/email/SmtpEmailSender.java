package com.locnguyen.ecommerce.infrastructure.email;

import com.locnguyen.ecommerce.common.config.email.SmtpEmailProperties;
import com.locnguyen.ecommerce.domains.verification.enums.VerificationPurpose;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** SMTP implementation for transactional OTP delivery. */
@Slf4j
@Component("smtpEmailSender")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.email.smtp", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private final SmtpEmailProperties properties;
    private JavaMailSender mailSender;

    @PostConstruct
    void initialize() {
        validateConfiguration();
        mailSender = createMailSender(properties);
        log.info("SMTP email sender configured: host={}, port={}, authEnabled={}, startTlsEnabled={}, from={}",
                properties.getHost(),
                properties.getPort(),
                properties.isAuth(),
                properties.isStarttlsEnabled(),
                redactEmail(properties.getFrom()));
    }

    @Override
    public void sendOtpEmail(String toEmail, String otp, VerificationPurpose purpose, int expiresMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getFrom());
            helper.setTo(toEmail);
            helper.setSubject(subjectFor(purpose));
            helper.setText(bodyFor(otp, purpose, expiresMinutes), false);
            mailSender.send(message);
            log.info("OTP email sent: purpose={}", purpose);
        } catch (MailException | jakarta.mail.MessagingException exception) {
            log.error("OTP email delivery failed: purpose={}, errorType={}", purpose,
                    exception.getClass().getSimpleName());
            throw new IllegalStateException("Unable to send OTP email", exception);
        }
    }

    private static JavaMailSender createMailSender(SmtpEmailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        if (properties.isAuth()) {
            sender.setUsername(properties.getUsername());
            sender.setPassword(properties.getPassword());
        }

        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.smtp.auth", Boolean.toString(properties.isAuth()));
        javaMailProperties.put("mail.smtp.starttls.enable", Boolean.toString(properties.isStarttlsEnabled()));
        javaMailProperties.put("mail.smtp.connectiontimeout", Integer.toString(properties.getConnectionTimeoutMs()));
        javaMailProperties.put("mail.smtp.timeout", Integer.toString(properties.getTimeoutMs()));
        javaMailProperties.put("mail.smtp.writetimeout", Integer.toString(properties.getWriteTimeoutMs()));
        return sender;
    }

    private void validateConfiguration() {
        requireText(properties.getHost(), "APP_EMAIL_SMTP_HOST");
        requireText(properties.getFrom(), "APP_EMAIL_FROM");
        if (properties.getPort() < 1 || properties.getPort() > 65_535) {
            throw new IllegalStateException("APP_EMAIL_SMTP_PORT must be between 1 and 65535");
        }
        if (properties.isAuth()) {
            requireText(properties.getUsername(), "APP_EMAIL_SMTP_USERNAME");
            requireText(properties.getPassword(), "APP_EMAIL_SMTP_PASSWORD");
        }
    }

    private static void requireText(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be set when SMTP email is enabled");
        }
    }

    private static String subjectFor(VerificationPurpose purpose) {
        return "Your " + purpose.name().replace('_', ' ').toLowerCase() + " verification code";
    }

    private static String bodyFor(String otp, VerificationPurpose purpose, int expiresMinutes) {
        return "Use this code to continue " + purpose.name().replace('_', ' ').toLowerCase()
                + ": " + otp + "\n\nThis code expires in " + expiresMinutes + " minutes.";
    }

    private static String redactEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
