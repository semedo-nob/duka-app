package com.duka.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PosFlowIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void barcodeSaleStockReceiptAndIdempotentMpesaCallback() throws Exception {
        String token = login();

        JsonNode milk = body(mvc.perform(get("/api/products/barcode/6161100000001").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
        int stock = milk.get("stock").asInt();
        long milkId = milk.get("id").asLong();

        String suffix = String.valueOf(System.nanoTime());
        JsonNode created = body(mvc.perform(post("/api/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Flour\",\"price\":100,\"cost\":80,\"sku\":\"TST-" + suffix + "\",\"barcode\":\"9" + suffix.substring(0, 12) + "\",\"cat\":\"Grocery\"}"))
                .andExpect(status().isCreated()).andReturn());
        assertTrue(created.get("barcode").asText().startsWith("9"));

        mvc.perform(post("/api/sales").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"cash\",\"items\":[{\"productId\":" + milkId + ",\"name\":\"Milk 500ml\",\"qty\":1,\"price\":65},{\"productId\":7,\"name\":\"Maize Flour 2kg\",\"qty\":1000,\"price\":215}]}"))
                .andExpect(status().isConflict());
        JsonNode afterReject = body(mvc.perform(get("/api/products/barcode/6161100000001").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
        assertEquals(stock, afterReject.get("stock").asInt());

        String saleKey = "it-cash-" + System.nanoTime();
        JsonNode sale = body(mvc.perform(post("/api/sales").header("Authorization", bearer(token))
                        .header("Idempotency-Key", saleKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"cash\",\"tendered\":100,\"items\":[{\"productId\":" + milkId + ",\"name\":\"Milk 500ml\",\"qty\":1,\"price\":65}]}"))
                .andExpect(status().isCreated()).andReturn());
        assertEquals("COMPLETED", sale.get("paymentStatus").asText());
        JsonNode again = body(mvc.perform(post("/api/sales").header("Authorization", bearer(token))
                        .header("Idempotency-Key", saleKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"cash\",\"items\":[{\"productId\":" + milkId + ",\"name\":\"Milk 500ml\",\"qty\":1,\"price\":65}]}"))
                .andExpect(status().isCreated()).andReturn());
        assertEquals(sale.get("id").asLong(), again.get("id").asLong());
        JsonNode afterCash = body(mvc.perform(get("/api/products/barcode/6161100000001").header("Authorization", bearer(token))).andReturn());
        assertEquals(stock - 1, afterCash.get("stock").asInt());

        JsonNode pending = body(mvc.perform(post("/api/sales").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"mpesa\",\"items\":[{\"productId\":" + milkId + ",\"name\":\"Milk 500ml\",\"qty\":1,\"price\":65}]}"))
                .andExpect(status().isCreated()).andReturn());
        assertEquals("PENDING", pending.get("paymentStatus").asText());
        assertEquals("AWAITING_PAYMENT", pending.get("status").asText());
        String ref = pending.get("paymentRef").asText();
        JsonNode duringPending = body(mvc.perform(get("/api/products/barcode/6161100000001").header("Authorization", bearer(token))).andReturn());
        assertEquals(stock - 1, duringPending.get("stock").asInt());

        mvc.perform(post("/api/integrations/mpesa/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalRef\":\"" + ref + "\",\"status\":\"SUCCESS\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode confirmed = body(mvc.perform(post("/api/integrations/mpesa/callback")
                        .header("X-Duka-Webhook-Secret", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalRef\":\"" + ref + "\",\"status\":\"SUCCESS\",\"providerReceipt\":\"RCPT1\"}"))
                .andExpect(status().isOk()).andReturn());
        assertEquals("COMPLETED", confirmed.get("paymentStatus").asText());
        body(mvc.perform(post("/api/integrations/mpesa/callback")
                        .header("X-Duka-Webhook-Secret", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalRef\":\"" + ref + "\",\"status\":\"SUCCESS\",\"providerReceipt\":\"RCPT1\"}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode afterMpesa = body(mvc.perform(get("/api/products/barcode/6161100000001").header("Authorization", bearer(token))).andReturn());
        assertEquals(stock - 2, afterMpesa.get("stock").asInt());

        String csv = "name,qty,unit_cost,barcode\nSugar 1kg,3,150,6161100000003\nUnknown spice,2,80,\n";
        MockMultipartFile file = new MockMultipartFile("file", "invoice.csv", "text/csv", csv.getBytes());
        JsonNode review = body(mvc.perform(multipart("/api/documents").file(file)
                        .param("supplierName", "ABC Wholesalers")
                        .param("invoiceNumber", "INV-20491")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated()).andReturn());
        long reviewId = review.get("id").asLong();
        long sugarLine = review.get("lines").get(0).get("id").asLong();
        long unknownLine = review.get("lines").get(1).get("id").asLong();
        assertEquals(3L, review.get("lines").get(0).get("matchedProductId").asLong());
        assertTrue(review.get("lines").get(1).get("matchedProductId").isNull());

        mvc.perform(post("/api/receipt-reviews/" + reviewId + "/approve").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());

        String edit = "{\"lines\":[{\"id\":" + unknownLine + ",\"removed\":true},{\"id\":" + sugarLine + ",\"quantity\":3,\"unitCost\":150,\"matchedProductId\":3}]}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/receipt-reviews/" + reviewId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edit))
                .andExpect(status().isOk());
        JsonNode sugarBefore = body(mvc.perform(get("/api/products/barcode/6161100000003").header("Authorization", bearer(token))).andReturn());
        mvc.perform(post("/api/receipt-reviews/" + reviewId + "/approve").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/receipt-reviews/" + reviewId + "/approve").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        JsonNode sugarAfter = body(mvc.perform(get("/api/products/barcode/6161100000003").header("Authorization", bearer(token))).andReturn());
        assertEquals(sugarBefore.get("stock").asInt() + 3, sugarAfter.get("stock").asInt());

        JsonNode movements = body(mvc.perform(get("/api/products/3/movements").header("Authorization", bearer(token))).andReturn());
        assertTrue(movements.toString().contains("PURCHASE"));
        assertTrue(movements.toString().contains("OPENING_BALANCE"));
    }

    private String login() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0712 345 678\",\"pin\":\"test-pin\"}"))
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
