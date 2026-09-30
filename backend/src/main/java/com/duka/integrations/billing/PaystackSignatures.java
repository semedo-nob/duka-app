package com.duka.integrations.billing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class PaystackSignatures {
    private PaystackSignatures() {}

    public static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign Paystack payload", ex);
        }
    }

    public static boolean matches(String secret, String body, String signature) {
        if (secret == null || secret.isBlank() || signature == null || signature.isBlank() || body == null) {
            return false;
        }
        byte[] expected = sign(secret, body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
