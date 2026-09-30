package com.duka.integrations.mpesa;

import com.duka.config.DukaProperties;
import com.duka.web.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class ConfiguredMpesaGateway implements MpesaGateway {
    private final DukaProperties properties;

    public ConfiguredMpesaGateway(DukaProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean mockMode() {
        return !"live".equalsIgnoreCase(properties.getMpesa().getMode());
    }

    @Override
    public Initiated initiate(BigDecimal amount, String accountReference) {
        if (mockMode()) {
            return new Initiated("MOCK-" + UUID.randomUUID(), "MOCK_MPESA");
        }
        DukaProperties.Mpesa mpesa = properties.getMpesa();
        boolean missing = blank(mpesa.getConsumerKey()) || blank(mpesa.getConsumerSecret())
                || blank(mpesa.getShortcode()) || blank(mpesa.getPasskey()) || blank(mpesa.getCallbackUrl());
        if (missing) {
            throw new ApiException(503, "M-Pesa live mode has no Daraja credentials. Set the MPESA_* environment variables, or use MPESA_MODE=mock.");
        }
        throw new ApiException(503, "The M-Pesa adapter is ready, but the official Daraja request contract is not wired in. Checkout will not report a successful payment.");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
