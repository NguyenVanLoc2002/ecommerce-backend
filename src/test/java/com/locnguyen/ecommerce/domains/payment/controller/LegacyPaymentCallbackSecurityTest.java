package com.locnguyen.ecommerce.domains.payment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LegacyPaymentCallbackSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postLegacyCallback_whenUnauthenticated_rejectsSpoofedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderCode\":\"ORD-SPOOFED\",\"status\":\"SUCCESS\"}"))
                .andExpect(status().isUnauthorized());
    }
}
