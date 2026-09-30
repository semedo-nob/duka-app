package com.duka.integrations.etims;

/**
 * Boundary for KRA eTIMS. Implementations must not mark an invoice accepted
 * unless a real tax-authority response says so.
 */
public interface EtimsAdapter {
    boolean configured();

    String mode();

    SubmissionResult submit(long saleId);

    record SubmissionResult(String status, String externalRef, String message) {}
}
