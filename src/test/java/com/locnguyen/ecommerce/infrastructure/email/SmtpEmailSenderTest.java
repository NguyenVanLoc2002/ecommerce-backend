package com.locnguyen.ecommerce.infrastructure.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.locnguyen.ecommerce.common.config.email.SmtpEmailProperties;
import com.locnguyen.ecommerce.domains.verification.enums.VerificationPurpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpEmailSenderTest {

    private static final String FROM = "sender@example.test";
    private static final String RECIPIENT = "recipient@example.test";
    private static final String OTP = "123456";
    private static final String SMTP_PASSWORD = "smtp-password-must-not-appear-in-logs";

    private GreenMail greenMail;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.start();

        Logger logger = (Logger) LoggerFactory.getLogger(SmtpEmailSender.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(SmtpEmailSender.class)).detachAppender(logAppender);
        logAppender.stop();
        greenMail.stop();
    }

    @Test
    void sendOtpEmail_withLocalSmtpSandbox_deliversMessageWithoutLoggingSecrets() throws Exception {
        SmtpEmailProperties properties = new SmtpEmailProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(ServerSetupTest.SMTP.getPort());
        properties.setFrom(FROM);
        properties.setAuth(false);
        properties.setUsername("smtp-user@example.test");
        properties.setPassword(SMTP_PASSWORD);
        properties.setStarttlsEnabled(false);

        SmtpEmailSender sender = new SmtpEmailSender(properties);
        sender.initialize();
        sender.sendOtpEmail(RECIPIENT, OTP, VerificationPurpose.FORGOT_PASSWORD, 5);

        assertThat(greenMail.waitForIncomingEmail(5_000, 1)).isTrue();
        jakarta.mail.internet.MimeMessage message = greenMail.getReceivedMessages()[0];
        assertThat(message.getFrom()[0].toString()).contains(FROM);
        assertThat(message.getAllRecipients()[0].toString()).contains(RECIPIENT);
        assertThat(message.getSubject()).contains("forgot password");
        assertThat(message.getContent().toString()).contains(OTP).contains("5 minutes");

        String logs = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(logs).doesNotContain(OTP).doesNotContain(SMTP_PASSWORD).doesNotContain(FROM);
    }
}
