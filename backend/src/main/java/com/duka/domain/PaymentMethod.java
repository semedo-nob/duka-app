package com.duka.domain;

public enum PaymentMethod {
    CASH,
    MPESA,
    CARD,
    CREDIT;

    public static PaymentMethod from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("payment method is required");
        }
        return PaymentMethod.valueOf(raw.trim().toUpperCase());
    }
}
