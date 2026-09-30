package com.duka.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class SecretTokens {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretTokens() {}

    public static String code() {
        char[] raw = new char[8];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(raw, 0, 4) + "-" + new String(raw, 4, 4);
    }

    public static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalize(code).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash token");
        }
    }

    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.replace("-", "").replace(" ", "").toUpperCase();
    }
}
