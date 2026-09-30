package com.duka.web;

import com.duka.repo.PlatformAdminRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingClosedIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformAdminRepository admins;

    @Test
    void checkoutStaysClosedWithoutPaystack() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime()));
        String token = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"05" + suffix.substring(0, 8) + "\",\"pin\":\"1234\",\"name\":\"Owner\",\"businessName\":\"Closed " + suffix + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String raw = token.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        TestApprovals.approve(mvc, json, encoder, admins, raw);
        mvc.perform(post("/api/account/password").header("Authorization", "Bearer " + raw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"1234\",\"password\":\"account-pass\"}"))
                .andExpect(status().isOk());
        String bearer = "Bearer " + raw;
        mvc.perform(post("/api/subscription/checkout").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"growth\",\"email\":\"owner@shop.test\",\"accountPassword\":\"account-pass\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("DUKA_BILLING_PROVIDER")));
    }
}
