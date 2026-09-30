package com.duka.web;

import com.duka.domain.PlatformAdmin;
import com.duka.domain.SubscriptionAccount;
import com.duka.integrations.billing.PaystackSignatures;
import com.duka.repo.PlatformAdminRepository;
import com.duka.repo.SubscriptionAccountRepository;
import com.duka.repo.SubscriptionPaymentRepository;
import com.duka.service.SubscriptionService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformBillingIT {
    @DynamicPropertySource
    static void billing(DynamicPropertyRegistry registry) {
        registry.add("duka.billing.provider", () -> "paystack");
        registry.add("duka.billing.secret-key", () -> "sk_test_duka_local_signature");
        registry.add("duka.billing.webhook-secret", () -> "sk_test_duka_local_signature");
        registry.add("duka.billing.api-base", () -> "http://127.0.0.1:9");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformAdminRepository admins;
    @Autowired SubscriptionAccountRepository subscriptions;
    @Autowired SubscriptionPaymentRepository payments;
    @Autowired SubscriptionService subscriptionService;

    @Test
    void paystackCheckoutFailureDoesNotUnlockAndASignedWebhookDoes() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime()));
        String token = register("07" + suffix.substring(0, 8), "1234", "Owner", "Shop " + suffix);
        TestApprovals.approve(mvc, json, encoder, admins, token);
        mvc.perform(post("/api/account/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"1234\",\"password\":\"account-pass\"}"))
                .andExpect(status().isOk());
        long businessId = body(mvc.perform(get("/api/account/me").header("Authorization", bearer(token))).andReturn()).get("businessId").asLong();
        JsonNode trial = body(mvc.perform(get("/api/subscription").header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn());
        assertEquals("TRIAL", trial.get("status").asText());
        assertFalse(trial.get("modules").get("purchasing").asBoolean());

        mvc.perform(post("/api/subscription/checkout").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"growth\",\"email\":\"owner@shop.test\",\"accountPassword\":\"account-pass\"}"))
                .andExpect(status().isBadGateway());
        assertFalse(body(mvc.perform(get("/api/capabilities").header("Authorization", bearer(token))).andReturn()).get("purchasing").asBoolean());

        mvc.perform(post("/api/purchase-orders").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplier\":\"Mill\",\"items\":1,\"total\":10}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/branches").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"First " + suffix + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/branches").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second " + suffix + "\"}"))
                .andExpect(status().isForbidden());

        String reference = "duka_ref_" + suffix;
        String payload = "{\"event\":\"charge.success\",\"data\":{\"id\":" + suffix.substring(0, 8) + ",\"reference\":\"" + reference + "\",\"currency\":\"KES\",\"metadata\":{\"businessId\":" + businessId + ",\"planCode\":\"growth\"}}}";
        String signature = PaystackSignatures.sign("sk_test_duka_local_signature", payload);
        mvc.perform(post("/api/integrations/billing/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("x-paystack-signature", "not-the-signature").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/integrations/billing/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("x-paystack-signature", signature).content(payload))
                .andExpect(status().isOk());
        mvc.perform(post("/api/integrations/billing/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("x-paystack-signature", signature).content(payload))
                .andExpect(status().isOk());
        assertEquals(1, payments.findByProviderAndProviderRef("paystack", reference).stream().count());
        assertTrue(body(mvc.perform(get("/api/capabilities").header("Authorization", bearer(token))).andReturn()).get("purchasing").asBoolean());
        mvc.perform(post("/api/purchase-orders").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplier\":\"Mill\",\"items\":1,\"total\":10}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/branches").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second " + suffix + "\"}"))
                .andExpect(status().isCreated());

        String failed = "{\"event\":\"charge.failed\",\"data\":{\"id\":8" + suffix.substring(0, 6) + ",\"reference\":\"fail_" + suffix + "\",\"metadata\":{\"businessId\":" + businessId + ",\"planCode\":\"growth\"}}}";
        mvc.perform(post("/api/integrations/billing/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("x-paystack-signature", PaystackSignatures.sign("sk_test_duka_local_signature", failed)).content(failed))
                .andExpect(status().isOk());
        assertTrue(body(mvc.perform(get("/api/capabilities").header("Authorization", bearer(token))).andReturn()).get("purchasing").asBoolean());
    }

    @Test
    void platformRolesAndSupportStaySeparated() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime()));
        String owner = register("08" + suffix.substring(0, 8), "1234", "Owner", "Support Shop " + suffix);
        long businessId = body(mvc.perform(get("/api/account/me").header("Authorization", bearer(owner))).andReturn()).get("businessId").asLong();
        mvc.perform(get("/api/platform/dashboard").header("Authorization", bearer(owner))).andExpect(status().isForbidden());

        String supportToken = platform("071" + suffix.substring(0, 7), "SUPPORT_ADMIN");
        String billingToken = platform("072" + suffix.substring(0, 7), "BILLING_ADMIN");
        String auditorToken = platform("073" + suffix.substring(0, 7), "PLATFORM_AUDITOR");
        String superToken = platform("074" + suffix.substring(0, 7), "SUPER_ADMIN");

        mvc.perform(get("/api/platform/dashboard").header("Authorization", bearer(superToken))).andExpect(status().isOk());
        String planCode = "p" + suffix.substring(0, 6);
        mvc.perform(post("/api/platform/plans").header("Authorization", bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + planCode + "\",\"name\":\"Plus\",\"description\":\"Plus\",\"price\":100,\"currency\":\"KES\",\"interval\":\"MONTH\",\"status\":\"ACTIVE\",\"modules\":[\"purchasing\"]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/platform/businesses/" + businessId).header("Authorization", bearer(superToken))).andExpect(status().isOk());
        mvc.perform(get("/api/platform/payments").header("Authorization", bearer(billingToken))).andExpect(status().isOk());
        mvc.perform(get("/api/platform/billing-events").header("Authorization", bearer(billingToken))).andExpect(status().isOk());
        mvc.perform(post("/api/platform/support/1/reply").header("Authorization", bearer(billingToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"no\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/payments").header("Authorization", bearer(supportToken))).andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/plans").header("Authorization", bearer(supportToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"nope\",\"name\":\"Nope\",\"price\":1,\"modules\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/audit").header("Authorization", bearer(auditorToken))).andExpect(status().isOk());
        mvc.perform(post("/api/platform/businesses/" + businessId + "/active?active=false").header("Authorization", bearer(auditorToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/plans").header("Authorization", bearer(auditorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auditplan\",\"name\":\"Nope\",\"price\":1,\"modules\":[]}"))
                .andExpect(status().isForbidden());

        JsonNode ticket = body(mvc.perform(post("/api/support").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"Printer\",\"message\":\"The receipt printer is offline\",\"category\":\"PRINTER\"}"))
                .andExpect(status().isCreated()).andReturn());
        long ticketId = ticket.get("id").asLong();
        mvc.perform(post("/api/platform/support/" + ticketId + "/reply").header("Authorization", bearer(supportToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Check the CUPS queue named by DUKA_PRINTER.\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/platform/support/" + ticketId + "/status?status=RESOLVED").header("Authorization", bearer(supportToken)))
                .andExpect(status().isOk());
        JsonNode seen = body(mvc.perform(get("/api/support/" + ticketId).header("Authorization", bearer(owner))).andExpect(status().isOk()).andReturn());
        assertTrue(seen.toString().contains("CUPS"));
        assertEquals("RESOLVED", seen.get("status").asText());
        mvc.perform(get("/api/platform/businesses/" + businessId + "/diagnostics").header("Authorization", bearer(supportToken)))
                .andExpect(status().isOk());
    }

    @Test
    void adminGrantUnlocksWithoutCreatingAPaystackPayment() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime()));
        String owner = register("05" + suffix.substring(0, 8), "1234", "Owner", "Grant Shop " + suffix);
        TestApprovals.approve(mvc, json, encoder, admins, owner);
        long businessId = body(mvc.perform(get("/api/account/me").header("Authorization", bearer(owner))).andReturn()).get("businessId").asLong();
        String supportToken = platform("076" + suffix.substring(0, 7), "SUPPORT_ADMIN");
        String billingToken = platform("077" + suffix.substring(0, 7), "BILLING_ADMIN");
        String grant = "{\"planCode\":\"growth\",\"days\":30,\"reason\":\"Customer paid offline at the counter\",\"source\":\"ADMIN_GRANT\"}";
        mvc.perform(post("/api/platform/businesses/" + businessId + "/grants").header("Authorization", bearer(supportToken))
                        .contentType(MediaType.APPLICATION_JSON).content(grant))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/businesses/" + businessId + "/grants").header("Authorization", bearer(billingToken))
                        .contentType(MediaType.APPLICATION_JSON).content(grant))
                .andExpect(status().isOk());
        JsonNode subscription = body(mvc.perform(get("/api/subscription").header("Authorization", bearer(owner))).andReturn());
        assertEquals("ACTIVE", subscription.get("status").asText());
        assertEquals("ADMIN_GRANT", subscription.get("activationSource").asText());
        assertTrue(subscription.get("modules").get("purchasing").asBoolean());
        assertEquals(0, payments.findByBusinessIdOrderByCreatedAtDesc(businessId).stream().filter(row -> "paystack".equals(row.getProvider())).count());
        assertTrue(subscriptions.findByBusinessIdOrderByCreatedAtDesc(businessId).stream().anyMatch(row -> "ADMIN_GRANT".equals(row.getProvider())));
        mvc.perform(post("/api/purchase-orders").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplier\":\"Mill\",\"items\":1,\"total\":10}"))
                .andExpect(status().isCreated());
        JsonNode business = body(mvc.perform(get("/api/platform/businesses/" + businessId).header("Authorization", bearer(billingToken))).andReturn());
        assertTrue(business.get("billingDiagnosis").get("likelyCause").asText().length() > 10);
    }

    @Test
    void expiryKeepsCatalogueAndTenantsStayApart() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime()));
        String ownerA = register("09" + suffix.substring(0, 8), "1234", "A", "Tenant A " + suffix);
        String ownerB = register("06" + suffix.substring(0, 8), "1234", "B", "Tenant B " + suffix);
        TestApprovals.approve(mvc, json, encoder, admins, ownerA);
        TestApprovals.approve(mvc, json, encoder, admins, ownerB);
        long businessId = body(mvc.perform(get("/api/account/me").header("Authorization", bearer(ownerA))).andReturn()).get("businessId").asLong();
        String sku = "LIFE-" + suffix.substring(0, 6);
        JsonNode created = body(mvc.perform(post("/api/products").header("Authorization", bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kept Flour " + suffix + "\",\"price\":100,\"cost\":50,\"sku\":\"" + sku + "\",\"cat\":\"Grocery\"}"))
                .andExpect(status().isCreated()).andReturn());
        long productId = created.get("id").asLong();
        mvc.perform(post("/api/products/" + productId + "/receive").header("Authorization", bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qty\":5,\"cost\":50}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/expenses").header("Authorization", bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":40,\"category\":\"Rent\",\"method\":\"cash\",\"description\":\"Private rent " + suffix + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/sales").header("Authorization", bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"cash\",\"tendered\":100,\"items\":[{\"productId\":" + productId + ",\"name\":\"Kept Flour " + suffix + "\",\"qty\":1,\"price\":100}]}"))
                .andExpect(status().isCreated());

        assertFalse(body(mvc.perform(get("/api/products").header("Authorization", bearer(ownerB))).andReturn()).toString().contains(sku));
        assertFalse(body(mvc.perform(get("/api/expenses").header("Authorization", bearer(ownerB))).andReturn()).toString().contains("Private rent " + suffix));
        assertFalse(body(mvc.perform(get("/api/sales").header("Authorization", bearer(ownerB))).andReturn()).toString().contains("Kept Flour " + suffix));
        assertFalse(body(mvc.perform(get("/api/support").header("Authorization", bearer(ownerB))).andReturn()).toString().contains("Private rent"));

        SubscriptionAccount trial = subscriptions.findByBusinessIdOrderByCreatedAtDesc(businessId).get(0);
        trial.setStatus("TRIAL");
        trial.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        subscriptions.save(trial);
        subscriptionService.advanceLifecycle();
        JsonNode after = body(mvc.perform(get("/api/products").header("Authorization", bearer(ownerA))).andExpect(status().isOk()).andReturn());
        assertTrue(after.toString().contains("Kept Flour " + suffix));
        JsonNode subscription = body(mvc.perform(get("/api/subscription").header("Authorization", bearer(ownerA))).andReturn());
        assertEquals("EXPIRED", subscription.get("status").asText());
    }

    private String platform(String phone, String role) throws Exception {
        PlatformAdmin admin = new PlatformAdmin();
        admin.setName(role);
        admin.setPhone(phone);
        admin.setPinHash(encoder.encode("4321"));
        admin.setRole(role);
        admins.save(admin);
        return body(mvc.perform(post("/api/platform/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"pin\":\"4321\"}"))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
    }

    private String register(String phone, String pin, String name, String business) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"pin\":\"" + pin + "\",\"name\":\"" + name + "\",\"businessName\":\"" + business + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
