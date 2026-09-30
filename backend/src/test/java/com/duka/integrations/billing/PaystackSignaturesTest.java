package com.duka.integrations.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaystackSignaturesTest {
    @Test
    void acceptsThePaystackHmacAndRejectsATamperedBody() {
        String body = "{\"event\":\"charge.success\",\"data\":{\"id\":9}}";
        String signature = PaystackSignatures.sign("test-paystack-secret", body);
        assertTrue(PaystackSignatures.matches("test-paystack-secret", body, signature));
        assertFalse(PaystackSignatures.matches("test-paystack-secret", body + " ", signature));
        assertFalse(PaystackSignatures.matches("other-secret", body, signature));
        assertFalse(PaystackSignatures.matches("test-paystack-secret", body, ""));
    }
}
