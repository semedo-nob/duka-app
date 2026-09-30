package com.duka.config;

import com.duka.domain.Business;
import com.duka.domain.PlatformAdmin;
import com.duka.domain.UserAccount;
import com.duka.domain.UserRole;
import com.duka.repo.BusinessRepository;
import com.duka.repo.PlatformAdminRepository;
import com.duka.repo.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapSeeder implements ApplicationRunner {
    private final UserAccountRepository users;
    private final BusinessRepository businesses;
    private final PlatformAdminRepository platformAdmins;
    private final PasswordEncoder encoder;
    private final DukaProperties properties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        seedOwner();
        seedPlatformAdmin();
    }

    private void seedOwner() {
        if (users.count() > 0) {
            return;
        }
        String phone = Phones.normalize(properties.getBootstrap().getPhone());
        String pin = properties.getBootstrap().getPin();
        if (phone.isBlank() || pin == null || pin.isBlank()) {
            log.warn("No login user exists. Set DUKA_BOOTSTRAP_PHONE and DUKA_BOOTSTRAP_PIN to create the first owner.");
            return;
        }
        Business business = businesses.findAll().stream().findFirst().orElseGet(() -> {
            Business created = new Business();
            created.setName("Duka");
            return businesses.save(created);
        });
        UserAccount owner = new UserAccount();
        owner.setName(properties.getBootstrap().getName());
        owner.setPhone(phone);
        owner.setBusinessId(business.getId());
        owner.setPinHash(encoder.encode(pin));
        owner.setRole(UserRole.OWNER);
        users.save(owner);
        log.info("Created bootstrap owner for phone {}", phone);
    }

    private void seedPlatformAdmin() {
        if (platformAdmins.count() > 0) {
            return;
        }
        String phone = properties.getPlatform().getPhone();
        String pin = properties.getPlatform().getPin();
        if (phone == null || phone.isBlank() || pin == null || pin.isBlank()) {
            return;
        }
        boolean demo = "0700000000".equals(Phones.normalize(phone)) || "123456".equals(pin);
        if (demo && !demoProfile()) {
            log.error("Refusing development platform credentials. Set a real DUKA_PLATFORM_ADMIN_PHONE and DUKA_PLATFORM_ADMIN_PIN for this profile.");
            return;
        }
        if (demo) {
            log.warn("Created the development platform administrator {}. This login is not for production.", Phones.normalize(phone));
        }
        PlatformAdmin admin = new PlatformAdmin();
        admin.setName(properties.getPlatform().getName());
        admin.setPhone(Phones.normalize(phone));
        admin.setPinHash(encoder.encode(pin));
        admin.setRole("SUPER_ADMIN");
        platformAdmins.save(admin);
        log.info("Created platform administrator for phone {}", admin.getPhone());
    }

    private boolean demoProfile() {
        // Default profile "dev" is not listed by getActiveProfiles().
        return environment.acceptsProfiles(Profiles.of("dev", "test"));
    }
}
