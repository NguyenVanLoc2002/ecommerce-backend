package com.locnguyen.ecommerce.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locnguyen.ecommerce.domains.verification.enums.VerificationPurpose;
import com.locnguyen.ecommerce.domains.verification.repository.VerificationTokenRepository;
import com.locnguyen.ecommerce.infrastructure.email.EmailSender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.csrf-double-submit-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CustomerAccountSecurityIntegrationTest {

    private static final String PASSWORD = "FixturePassword123";
    private static final String COOKIE_NAME = "account-integration-refresh";

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.3")
            .withDatabaseName("account_security_test").withUsername("test").withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureServices(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.auth.refresh-cookie.name", () -> COOKIE_NAME);
        registry.add("app.jwt.secret", () -> "isolated-account-integration-signing-key".repeat(2));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired VerificationTokenRepository verificationTokenRepository;
    @MockBean EmailSender emailSender;

    @Test
    void supportsDeviceDiscoveryAndImmediatelyRevokesPreviousAndRotatedAccess() throws Exception {
        String email = randomEmail();
        Session retained = register(email);
        Session other = login(email);

        MvcResult listed = mvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", retained.authorization()).cookie(retained.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].current").value(true))
                .andExpect(jsonPath("$.data[1].current").value(false))
                .andExpect(jsonPath("$.data[1].tokenHash").doesNotExist())
                .andExpect(jsonPath("$.data[1].ipAddress").doesNotExist())
                .andReturn();
        String otherFamily = json.readTree(listed.getResponse().getContentAsString()).at("/data/1/id").asText();
        Session rotated = session(mvc.perform(post("/api/v1/auth/refresh-token").cookie(other.cookie()))
                .andExpect(status().isOk()).andReturn(), "/data/accessToken");

        mvc.perform(delete("/api/v1/auth/sessions/{id}", otherFamily)
                        .header("Authorization", retained.authorization()).cookie(retained.cookie()))
                .andExpect(status().isOk());

        for (Session revoked : new Session[]{other, rotated}) {
            mvc.perform(get("/api/v1/me").header("Authorization", revoked.authorization()))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/refresh-token").cookie(rotated.cookie()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", retained.authorization()))
                .andExpect(status().isOk());
    }

    @Test
    void verifiesOnlyTheAuthenticatedEmailAndPersistsInvalidAttempts() throws Exception {
        String email = randomEmail();
        Session customer = register(email);
        mvc.perform(post("/api/v1/account/email-verification"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", customer.authorization()))
                .andExpect(jsonPath("$.data.emailVerified").value(false));
        mvc.perform(post("/api/v1/account/email-verification").header("Authorization", customer.authorization()))
                .andExpect(status().isOk());

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendOtpEmail(eq(email), code.capture(), eq(VerificationPurpose.VERIFY_EMAIL), anyInt());
        String wrongCode = code.getValue().equals("000000") ? "000001" : "000000";
        mvc.perform(post("/api/v1/account/email-verification/confirm")
                        .header("Authorization", customer.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("otp", wrongCode))))
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
        assertThat(verificationTokenRepository
                .findActiveByTargetAndPurpose(email, VerificationPurpose.VERIFY_EMAIL, LocalDateTime.now())
                .get(0).getAttemptCount()).isEqualTo(1);

        mvc.perform(post("/api/v1/account/email-verification/confirm")
                        .header("Authorization", customer.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("otp", code.getValue()))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization", customer.authorization()))
                .andExpect(jsonPath("$.data.emailVerified").value(true));
        assertThat(verificationTokenRepository
                .findActiveByTargetAndPurpose(email, VerificationPurpose.VERIFY_EMAIL, LocalDateTime.now())).isEmpty();
    }

    private Session register(String email) throws Exception {
        return session(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "password", PASSWORD, "firstName", "Fixture", "lastName", "Customer"))))
                .andExpect(status().isCreated()).andReturn(), "/data/tokens/accessToken");
    }

    private Session login(String email) throws Exception {
        return session(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn(), "/data/tokens/accessToken");
    }

    private Session session(MvcResult result, String tokenPointer) throws Exception {
        String access = json.readTree(result.getResponse().getContentAsString()).at(tokenPointer).asText();
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(access).isNotBlank();
        assertThat(setCookie).startsWith(COOKIE_NAME + "=");
        String refresh = setCookie.substring(COOKIE_NAME.length() + 1, setCookie.indexOf(';'));
        return new Session("Bearer " + access, new Cookie(COOKIE_NAME, refresh));
    }

    private static String randomEmail() {
        return "account-" + UUID.randomUUID() + "@example.test";
    }

    private record Session(String authorization, Cookie cookie) {
    }
}
