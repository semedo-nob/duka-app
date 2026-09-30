package com.duka.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal money(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** VAT included in a shelf price: gross * rate / (1 + rate). */
    public static BigDecimal includedTax(BigDecimal gross, BigDecimal rate) {
        BigDecimal amount = money(gross);
        BigDecimal taxRate = nz(rate);
        if (amount.signum() == 0 || taxRate.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(taxRate).divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
    }
}
