package com.duka.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationsIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void supplierPurchasePermissionsAndSubscriptionStayHonest() throws Exception {
        String owner = login("0712345678", "test-pin");
        String suffix = String.valueOf(System.nanoTime());

        JsonNode supplier = body(mvc.perform(post("/api/suppliers").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unga " + suffix + "\",\"phone\":\"0700111222\",\"category\":\"Flour\",\"balance\":1500}"))
                .andExpect(status().isCreated()).andReturn());
        long supplierId = supplier.get("id").asLong();
        assertEquals("0700111222", supplier.get("phone").asText());

        mvc.perform(put("/api/suppliers/" + supplierId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unga " + suffix + "\",\"phone\":\"0700111223\",\"active\":false}"))
                .andExpect(status().isOk());
        JsonNode detail = body(mvc.perform(get("/api/suppliers/" + supplierId).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn());
        assertFalse(detail.get("supplier").get("active").asBoolean());

        JsonNode categories = body(mvc.perform(post("/api/category-templates/electronics/apply").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn());
        assertTrue(categories.toString().contains("Phones"));
        assertTrue(categories.toString().contains("Chargers"));

        String cashierPhone = "0799" + suffix.substring(suffix.length() - 6);
        JsonNode invited = body(mvc.perform(post("/api/team").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Till " + suffix + "\",\"phone\":\"" + cashierPhone + "\",\"role\":\"Cashier\"}"))
                .andExpect(status().isCreated()).andReturn());
        String cashier = body(mvc.perform(post("/api/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + invited.get("invitationCode").asText() + "\",\"pin\":\"4321\"}"))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
        mvc.perform(post("/api/suppliers").header("Authorization", bearer(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Should fail\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/capabilities/purchasing/unlock").header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden());
        JsonNode caps = body(mvc.perform(get("/api/capabilities").header("Authorization", bearer(owner))).andExpect(status().isOk()).andReturn());
        assertFalse(caps.get("purchasing").asBoolean());
        mvc.perform(post("/api/account/password").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"test-pin\",\"password\":\"account-pass\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/subscription/checkout").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"growth\",\"accountPassword\":\"account-pass\"}"))
                .andExpect(status().isServiceUnavailable());

        JsonNode product = body(mvc.perform(post("/api/products").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flour " + suffix + "\",\"price\":200,\"cost\":150,\"sku\":\"FL-" + suffix + "\",\"cat\":\"Grocery\"}"))
                .andExpect(status().isCreated()).andReturn());
        long productId = product.get("id").asLong();
        mvc.perform(post("/api/purchase-orders").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"lines\":[{\"productId\":" + productId + ",\"qty\":4,\"unitCost\":150}]}"))
                .andExpect(status().isForbidden());
    }

    private String login(String phone, String pin) throws Exception {
        JsonNode body = body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk()).andReturn());
        return body.get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
