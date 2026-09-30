package com.duka.web;

import com.duka.integrations.billing.PaystackSignatures;
import com.duka.repo.PlatformAdminRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommercialLifecycleIT {
    @DynamicPropertySource
    static void billing(DynamicPropertyRegistry registry) {
        registry.add("duka.billing.provider", () -> "paystack");
        registry.add("duka.billing.secret-key", () -> "test-paystack-secret");
        registry.add("duka.billing.webhook-secret", () -> "test-paystack-secret");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformAdminRepository admins;

    @Test
    void approvalThenVerifiedPaystackWebhookUnlocksPurchasingOnce() throws Exception {
        String phone = "07" + String.valueOf(System.nanoTime()).substring(0, 8);
        JsonNode registered = body(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"pin\":\"tillpin\",\"name\":\"Owner\",\"businessName\":\"Lifecycle Shop\",\"email\":\"owner@lifecycle.test\"}"))
                .andExpect(status().isOk()).andReturn());
        String ownerToken = registered.get("token").asText();
        long businessId = registered.get("user").get("businessId").asLong();

        mvc.perform(post("/api/products").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sugar\",\"price\":100}"))
                .andExpect(status().isForbidden());

        String platform = TestApprovals.approve(mvc, json, encoder, admins, ownerToken);
        mvc.perform(get("/api/products").header("Authorization", bearer(platform))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/businesses").header("Authorization", bearer(ownerToken))).andExpect(status().isForbidden());

        mvc.perform(post("/api/products").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sugar\",\"price\":100,\"sku\":\"LIFE-" + businessId + "\"}"))
                .andExpect(status().isCreated());

        JsonNode before = body(mvc.perform(get("/api/subscription").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andReturn());
        assertEquals("TRIAL", before.get("status").asText());
        assertFalse(before.get("modules").get("purchasing").asBoolean());

        String reference = "duka_it_" + businessId;
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":" + businessId + ",\"reference\":\"" + reference
                + "\",\"currency\":\"KES\",\"metadata\":{\"businessId\":" + businessId + ",\"planCode\":\"growth\"}}}";
        mvc.perform(post("/api/integrations/billing/webhook")
                        .header("x-paystack-signature", "not-a-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        String signature = PaystackSignatures.sign("test-paystack-secret", payload);
        JsonNode accepted = body(mvc.perform(post("/api/integrations/billing/webhook")
                        .header("x-paystack-signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk()).andReturn());
        assertFalse(accepted.get("duplicate").asBoolean());

        JsonNode again = body(mvc.perform(post("/api/integrations/billing/webhook")
                        .header("x-paystack-signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk()).andReturn());
        assertTrue(again.get("duplicate").asBoolean());

        JsonNode after = body(mvc.perform(get("/api/subscription").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andReturn());
        assertEquals("ACTIVE", after.get("status").asText());
        assertTrue(after.get("modules").get("purchasing").asBoolean());
        assertEquals(1, after.get("payments").size());

        mvc.perform(post("/api/platform/businesses/" + businessId + "/suspend")
                        .header("Authorization", bearer(platform))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Support hold\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/products").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blocked\",\"price\":10}"))
                .andExpect(status().isForbidden());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
