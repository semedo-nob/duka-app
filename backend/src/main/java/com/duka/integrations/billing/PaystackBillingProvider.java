package com.duka.integrations.billing;

import com.duka.config.DukaProperties;
import com.duka.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paystack transaction initialize + webhook signature check.
 * A checkout URL is not a payment. Modules stay locked until a signed charge.success event is stored.
 */
@Component
@RequiredArgsConstructor
public class PaystackBillingProvider {
    private final DukaProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public boolean configured() {
        return "paystack".equalsIgnoreCase(provider()) && !secret().isBlank();
    }

    public String provider() {
        String value = properties.getBilling().getProvider();
        return value == null || value.isBlank() ? "unconfigured" : value.trim();
    }

    public String webhookSecret() {
        String dedicated = properties.getBilling().getWebhookSecret();
        if (dedicated != null && !dedicated.isBlank()) {
            return dedicated;
        }
        return secret();
    }

    public Map<String, Object> initialize(String email, BigDecimal amount, String currency, String reference,
                                          Long businessId, String planCode) {
        if (!configured()) {
            throw new ApiException(503, configurationBlocker());
        }
        if (!"paystack".equalsIgnoreCase(provider())) {
            throw new ApiException(503, "Duka's billing adapter is Paystack. Set DUKA_BILLING_PROVIDER=paystack and DUKA_BILLING_SECRET_KEY.");
        }
        int minor = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        ObjectNode body = json.createObjectNode();
        body.put("email", email);
        body.put("amount", minor);
        body.put("currency", currency);
        body.put("reference", reference);
        if (properties.getBilling().getCallbackUrl() != null && !properties.getBilling().getCallbackUrl().isBlank()) {
            body.put("callback_url", properties.getBilling().getCallbackUrl());
        }
        ObjectNode metadata = body.putObject("metadata");
        metadata.put("businessId", businessId);
        metadata.put("planCode", planCode);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase() + "/transaction/initialize"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + secret())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode tree = json.readTree(response.body().isBlank() ? "{}" : response.body());
            if (response.statusCode() >= 300 || !tree.path("status").asBoolean(false)) {
                String message = tree.path("message").asText("Paystack did not start checkout");
                throw new ApiException(502, "Paystack rejected checkout: " + message);
            }
            String url = tree.path("data").path("authorization_url").asText("");
            if (url.isBlank()) {
                throw new ApiException(502, "Paystack did not return a checkout URL");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("authorizationUrl", url);
            result.put("reference", tree.path("data").path("reference").asText(reference));
            result.put("status", "PENDING");
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(502, "Could not reach Paystack. Checkout was not started and nothing was unlocked.");
        }
    }

    /**
     * Paystack Kenya mobile-money charge. Paystack sends the M-Pesa prompt.
     * A pending charge is not a payment. Call {@link #verify(String)} before unlocking anything.
     */
    public JsonNode chargeMpesa(String email, BigDecimal amount, String currency, String reference,
                                String phone, Long businessId, String planCode) {
        if (!configured()) {
            throw new ApiException(503, configurationBlocker());
        }
        int minor = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        ObjectNode body = json.createObjectNode();
        body.put("email", email);
        body.put("amount", minor);
        body.put("currency", currency);
        body.put("reference", reference);
        ObjectNode mobile = body.putObject("mobile_money");
        mobile.put("phone", toKenyaMsisdn(phone));
        mobile.put("provider", "mpesa");
        ObjectNode metadata = body.putObject("metadata");
        metadata.put("businessId", businessId);
        metadata.put("planCode", planCode);
        JsonNode tree = post("/charge", body);
        if (!tree.path("status").asBoolean(false)) {
            throw new ApiException(502, "Paystack rejected the M-Pesa charge: " + tree.path("message").asText("no message"));
        }
        return tree;
    }

    public JsonNode verify(String reference) {
        if (!configured()) {
            throw new ApiException(503, configurationBlocker());
        }
        if (reference == null || !reference.matches("[A-Za-z0-9_\\-]{6,100}")) {
            throw new ApiException(400, "Payment reference is not valid");
        }
        String encoded = URLEncoder.encode(reference, StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase() + "/transaction/verify/" + encoded))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + secret())
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode tree = json.readTree(response.body().isBlank() ? "{}" : response.body());
            if (response.statusCode() >= 300 || !tree.path("status").asBoolean(false)) {
                throw new ApiException(502, "Paystack could not verify this reference: " + tree.path("message").asText("no message"));
            }
            return tree;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(502, "Could not reach Paystack to verify the payment. Nothing was unlocked.");
        }
    }

    private JsonNode post(String path, ObjectNode body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + secret())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return json.readTree(response.body().isBlank() ? "{}" : response.body());
        } catch (Exception ex) {
            throw new ApiException(502, "Could not reach Paystack. Checkout was not started and nothing was unlocked.");
        }
    }

    private static String toKenyaMsisdn(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.startsWith("0") && digits.length() == 10) {
            digits = "254" + digits.substring(1);
        }
        if (digits.startsWith("254") && digits.length() == 12) {
            return digits;
        }
        throw new ApiException(400, "Enter the M-Pesa number as 07… or 2547…");
    }

    public String configurationBlocker() {
        if (!"paystack".equalsIgnoreCase(provider())) {
            return "No billing provider is configured. Set DUKA_BILLING_PROVIDER=paystack and DUKA_BILLING_SECRET_KEY. Paid modules stay locked.";
        }
        return "Paystack is selected but DUKA_BILLING_SECRET_KEY is missing. Paid modules stay locked until Paystack verifies a charge.";
    }

    private String secret() {
        String value = properties.getBilling().getSecretKey();
        return value == null ? "" : value.trim();
    }

    private String apiBase() {
        String base = properties.getBilling().getApiBase();
        if (base == null || base.isBlank()) {
            return "https://api.paystack.co";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
