package com.duka.config;

public final class Phones {
    private Phones() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", "");
    }
}
