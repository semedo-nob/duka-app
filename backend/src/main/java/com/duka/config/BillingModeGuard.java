package com.duka.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Development may set DUKA_BILLING_PROVIDER=mock. Production must not start in that mode.
 */
@Component
@RequiredArgsConstructor
public class BillingModeGuard implements ApplicationRunner {
    private final DukaProperties properties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String provider = properties.getBilling().getProvider();
        boolean mock = provider != null && provider.equalsIgnoreCase("mock");
        if (mock && environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException("DUKA_BILLING_PROVIDER=mock is refused when SPRING_PROFILES_ACTIVE includes prod. Production requires Paystack verification.");
        }
    }
}
