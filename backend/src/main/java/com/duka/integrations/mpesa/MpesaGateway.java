package com.duka.integrations.mpesa;

import java.math.BigDecimal;

/**
 * Boundary for Safaricom Daraja. The mock implementation never calls Safaricom.
 * Live mode refuses to pretend a payment succeeded when credentials or the official contract are absent.
 */
public interface MpesaGateway {
    boolean mockMode();

    Initiated initiate(BigDecimal amount, String accountReference);

    record Initiated(String externalRef, String provider) {}
}
