package com.duka.integrations.etims;

import com.duka.config.DukaProperties;
import org.springframework.stereotype.Component;

@Component
public class UnconfiguredEtimsAdapter implements EtimsAdapter {
    private final DukaProperties properties;

    public UnconfiguredEtimsAdapter(DukaProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean configured() {
        DukaProperties.Etims etims = properties.getEtims();
        return etims.getBaseUrl() != null && !etims.getBaseUrl().isBlank()
                && etims.getTin() != null && !etims.getTin().isBlank()
                && !"unconfigured".equalsIgnoreCase(etims.getMode());
    }

    @Override
    public String mode() {
        return properties.getEtims().getMode() == null ? "unconfigured" : properties.getEtims().getMode();
    }

    @Override
    public SubmissionResult submit(long saleId) {
        if (!configured()) {
            return new SubmissionResult(
                    "PENDING",
                    null,
                    "eTIMS adapter is not connected. This invoice stays pending until the official OSCU/VSCU contract and credentials are configured. Nothing was sent to KRA."
            );
        }
        return new SubmissionResult(
                "PENDING",
                null,
                "eTIMS credentials are present, but this build does not call an invented KRA endpoint. The invoice stays pending."
        );
    }
}
