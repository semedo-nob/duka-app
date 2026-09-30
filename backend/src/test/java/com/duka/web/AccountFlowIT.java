package com.duka.web;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountFlowIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformAdminRepository admins;

    @Test
    void accountsAreIsolatedAndStaffCannotAdministerTheBusiness() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime()));
        String ownerPhone = "07" + suffix.substring(0, 8);
        String otherPhone = "08" + suffix.substring(0, 8);
        String cashierPhone = "09" + suffix.substring(0, 8);
        String ownerToken = register(ownerPhone, "1234", "Owner A", "Shop A " + suffix);
        String otherToken = register(otherPhone, "1234", "Owner B", "Shop B " + suffix);
        TestApprovals.approve(mvc, json, encoder, admins, ownerToken);
        TestApprovals.approve(mvc, json, encoder, admins, otherToken);

        String sku = "ISO-" + suffix.substring(0, 8);
        mvc.perform(post("/api/products").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Isolated item\",\"price\":10,\"cost\":5,\"sku\":\"" + sku + "\",\"cat\":\"Grocery\"}"))
                .andExpect(status().isCreated());
        JsonNode otherProducts = body(mvc.perform(get("/api/products").header("Authorization", bearer(otherToken))).andExpect(status().isOk()).andReturn());
        assertFalse(otherProducts.toString().contains(sku));

        mvc.perform(post("/api/customers").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Private Customer " + suffix + "\",\"phone\":\"0700\"}"))
                .andExpect(status().isCreated());
        JsonNode otherCustomers = body(mvc.perform(get("/api/customers").header("Authorization", bearer(otherToken))).andExpect(status().isOk()).andReturn());
        assertFalse(otherCustomers.toString().contains("Private Customer " + suffix));

        JsonNode invited = body(mvc.perform(post("/api/team").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cashier\",\"phone\":\"" + cashierPhone + "\",\"role\":\"Cashier\"}"))
                .andExpect(status().isCreated()).andReturn());
        String code = invited.get("invitationCode").asText();
        assertTrue(code.contains("-"));
        String cashierToken = body(mvc.perform(post("/api/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"pin\":\"5678\"}"))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
        mvc.perform(post("/api/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"pin\":\"5678\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/products").header("Authorization", bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\",\"price\":10}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/team").header("Authorization", bearer(cashierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"phone\":\"0711111111\",\"role\":\"Manager\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/businesses").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/account/change-credential").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"1234\",\"newPin\":\"9999\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + ownerPhone + "\",\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
        String fresh = login(ownerPhone, "9999");
        assertNotEquals(ownerToken, fresh);

        JsonNode sessions = body(mvc.perform(get("/api/account/sessions").header("Authorization", bearer(fresh))).andExpect(status().isOk()).andReturn());
        String otherSession = null;
        for (JsonNode session : sessions) {
            if (!session.get("current").asBoolean() && !session.get("revoked").asBoolean()) {
                otherSession = session.get("id").asText();
            }
        }
        assertTrue(otherSession != null);
        mvc.perform(delete("/api/account/sessions/" + otherSession).header("Authorization", bearer(fresh)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/account/me").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isUnauthorized());

        String managerPhone = "06" + suffix.substring(0, 8);
        JsonNode managerInvite = body(mvc.perform(post("/api/team").header("Authorization", bearer(fresh))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Manager\",\"phone\":\"" + managerPhone + "\",\"role\":\"Manager\"}"))
                .andExpect(status().isCreated()).andReturn());
        String managerToken = body(mvc.perform(post("/api/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + managerInvite.get("invitationCode").asText() + "\",\"pin\":\"2468\"}"))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
        long managerId = managerInvite.get("id").asLong();
        mvc.perform(post("/api/account/password").header("Authorization", bearer(fresh))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"9999\",\"password\":\"account-pass\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/business/owner-transfer").header("Authorization", bearer(fresh))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + managerId + ",\"pin\":\"9999\",\"password\":\"account-pass\",\"confirm\":\"TRANSFER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/business/owner-transfer").header("Authorization", bearer(fresh))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + managerId + ",\"pin\":\"9999\",\"confirm\":\"TRANSFER\"}"))
                .andExpect(status().isForbidden());
        String newOwner = login(managerPhone, "2468");
        mvc.perform(get("/api/business").header("Authorization", bearer(newOwner))).andExpect(status().isOk());

        mvc.perform(post("/api/team/" + invited.get("id").asText() + "/active?active=false").header("Authorization", bearer(newOwner)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + cashierPhone + "\",\"pin\":\"5678\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode audit = body(mvc.perform(get("/api/audit").header("Authorization", bearer(newOwner))).andExpect(status().isOk()).andReturn());
        assertTrue(audit.toString().contains("USER_INVITED"));
        assertTrue(audit.toString().contains("OWNER_TRANSFERRED"));
        assertFalse(audit.toString().contains("9999"));
        assertFalse(audit.toString().contains("5678"));

        JsonNode recovery = body(mvc.perform(post("/api/auth/recovery/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + ownerPhone + "\"}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("NOT CONFIGURED", recovery.get("delivery").asText());
        assertFalse(recovery.has("code"));

        mvc.perform(get("/api/business/export/products").header("Authorization", bearer(newOwner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/business/export/products").header("Authorization", bearer(fresh)))
                .andExpect(status().isForbidden());
    }

    private String register(String phone, String pin, String name, String business) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"pin\":\"" + pin + "\",\"name\":\"" + name + "\",\"businessName\":\"" + business + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String login(String phone, String pin) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
