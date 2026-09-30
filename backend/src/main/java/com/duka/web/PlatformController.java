package com.duka.web;

import com.duka.config.Phones;
import com.duka.domain.DeviceRegistration;
import com.duka.domain.PlatformAdmin;
import com.duka.repo.DeviceRegistrationRepository;
import com.duka.repo.PlatformAdminRepository;
import com.duka.security.JwtService;
import com.duka.security.PlatformPrincipal;
import com.duka.service.AccountService;
import com.duka.service.AuditService;
import com.duka.service.PlatformService;
import com.duka.service.SubscriptionService;
import com.duka.service.SupportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformController {
    private final PlatformAdminRepository admins;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final DeviceRegistrationRepository devices;
    private final SupportService support;
    private final AuditService audit;
    private final AccountService accounts;
    private final PlatformService platform;
    private final SubscriptionService subscriptions;

    @PostMapping("/login")
    Map<String, Object> login(@Valid @RequestBody Login body) {
        if (admins.count() == 0) {
            throw new ApiException(503, "Platform administration is not configured. Set DUKA_PLATFORM_ADMIN_PHONE and DUKA_PLATFORM_ADMIN_PIN.");
        }
        PlatformAdmin admin = admins.findByPhone(Phones.normalize(body.phone()))
                .orElseThrow(() -> new ApiException(401, "Phone or PIN is incorrect"));
        if (!admin.isActive() || !encoder.matches(body.pin(), admin.getPinHash())) {
            throw new ApiException(401, "Phone or PIN is incorrect");
        }
        return Map.of(
                "token", jwt.issuePlatform(admin.getId(), admin.getName(), admin.getRole()),
                "name", admin.getName(),
                "role", admin.getRole()
        );
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    Map<String, String> me() {
        PlatformPrincipal admin = admin();
        return Map.of("name", admin.getName(), "role", admin.getRole());
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    Map<String, Object> dashboard() {
        return platform.dashboard();
    }

    @GetMapping("/businesses")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    List<Map<String, Object>> businesses() {
        return platform.businesses();
    }

    @GetMapping("/businesses/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    Map<String, Object> business(@PathVariable long id) {
        return platform.business(id);
    }

    @GetMapping("/businesses/{id}/diagnostics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT_ADMIN','BILLING_ADMIN')")
    Map<String, Object> diagnostics(@PathVariable long id) {
        return platform.diagnostics(id);
    }

    @PostMapping("/businesses/{id}/active")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    Map<String, Object> setActive(@PathVariable long id, @RequestParam boolean active, @RequestParam(required = false) String reason) {
        String why = reason == null || reason.isBlank() ? "Recorded from the platform console" : reason;
        return active ? platform.reactivate(id, why, admin().getName()) : platform.suspend(id, why, admin().getName());
    }

    @PostMapping("/businesses/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    Map<String, Object> approve(@PathVariable long id, @Valid @RequestBody Reason body) {
        return platform.approve(id, body.reason(), admin().getName());
    }

    @PostMapping("/businesses/{id}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    Map<String, Object> suspend(@PathVariable long id, @Valid @RequestBody Reason body) {
        return platform.suspend(id, body.reason(), admin().getName());
    }

    @PostMapping("/businesses/{id}/reactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    Map<String, Object> reactivate(@PathVariable long id, @Valid @RequestBody Reason body) {
        return platform.reactivate(id, body.reason(), admin().getName());
    }

    @PostMapping("/businesses/{id}/close")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    Map<String, Object> close(@PathVariable long id, @Valid @RequestBody Reason body) {
        return platform.close(id, body.reason(), admin().getName());
    }

    @PostMapping("/recover")
    Map<String, Object> recover(@Valid @RequestBody Recover body) {
        return platform.recoverAdmin(body.phone(), body.recoveryCode(), body.newPin());
    }

    @PostMapping("/devices/{deviceId}/revoke")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT_ADMIN')")
    Map<String, Object> revoke(@PathVariable String deviceId) {
        DeviceRegistration device = devices.findById(deviceId).orElseThrow(() -> new ApiException(404, "Device not found"));
        device.setRevoked(true);
        devices.save(device);
        audit.log(device.getBusinessId(), admin().getName(), "DEVICE_REVOKED", deviceId, "DEVICE", deviceId, "");
        return Map.of("deviceId", device.getDeviceId(), "revoked", true);
    }

    @GetMapping("/support")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT_ADMIN')")
    List<Map<String, Object>> supportCases() {
        return support.all();
    }

    @GetMapping("/support/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT_ADMIN')")
    Map<String, Object> supportCase(@PathVariable long id) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(support.platformGet(id));
        Object businessId = body.get("businessId");
        if (businessId instanceof Number number) {
            body.put("context", platform.supportContext(number.longValue()));
        }
        return body;
    }

    @PostMapping("/support/{id}/reply")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT_ADMIN')")
    Map<String, Object> reply(@PathVariable long id, @RequestBody Dto.SupportReply reply) {
        return support.reply(id, reply.message(), admin());
    }

    @PostMapping("/support/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT_ADMIN')")
    Map<String, Object> supportStatus(@PathVariable long id, @RequestParam String status) {
        return support.setStatus(id, status, admin());
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BILLING_ADMIN','PLATFORM_AUDITOR')")
    List<Map<String, Object>> subscriptionRows() {
        return subscriptions.statusRows();
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BILLING_ADMIN','PLATFORM_AUDITOR')")
    List<Map<String, Object>> plans() {
        return subscriptions.allPlans();
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BILLING_ADMIN')")
    Map<String, Object> createPlan(@RequestBody PlanBody body) {
        return subscriptions.savePlan(body.code(), body.name(), body.description(), body.price(), body.currency(),
                body.interval(), body.status(), body.modules(), body.branchLimit(), body.userLimit(), body.deviceLimit(),
                body.productLimit(), true, admin().getName());
    }

    @PutMapping("/plans/{code}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BILLING_ADMIN')")
    Map<String, Object> updatePlan(@PathVariable String code, @RequestBody PlanBody body) {
        return subscriptions.savePlan(code, body.name(), body.description(), body.price(), body.currency(),
                body.interval(), body.status(), body.modules(), body.branchLimit(), body.userLimit(), body.deviceLimit(),
                body.productLimit(), false, admin().getName());
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BILLING_ADMIN')")
    List<Map<String, Object>> payments() {
        return subscriptions.allPayments();
    }

    @GetMapping("/billing-events")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BILLING_ADMIN')")
    List<Map<String, Object>> billingEvents() {
        return subscriptions.allEvents();
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    List<Map<String, Object>> audit() {
        return platform.activity();
    }

    @PostMapping("/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    Map<String, Object> createAdmin(@Valid @RequestBody AdminBody body) {
        if (!List.of("SUPER_ADMIN", "SUPPORT_ADMIN", "BILLING_ADMIN", "PLATFORM_AUDITOR").contains(body.role())) {
            throw new ApiException(400, "Unknown platform role");
        }
        if (body.pin().length() < 4) {
            throw new ApiException(400, "PIN must be at least 4 characters");
        }
        String phone = Phones.normalize(body.phone());
        if (admins.findByPhone(phone).isPresent()) {
            throw new ApiException(409, "That phone already has a platform login");
        }
        PlatformAdmin created = new PlatformAdmin();
        created.setName(body.name().trim());
        created.setPhone(phone);
        created.setPinHash(encoder.encode(body.pin()));
        created.setRole(body.role());
        admins.save(created);
        audit.log(null, admin().getName(), "PLATFORM_USER_CREATED", created.getRole(), "PLATFORM_ADMIN", created.getId().toString(), "");
        return Map.of("id", created.getId(), "name", created.getName(), "role", created.getRole());
    }

    @PostMapping("/admins/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    Map<String, Object> changeRole(@PathVariable long id, @RequestParam String role) {
        if (!List.of("SUPER_ADMIN", "SUPPORT_ADMIN", "BILLING_ADMIN", "PLATFORM_AUDITOR").contains(role)) {
            throw new ApiException(400, "Unknown platform role");
        }
        PlatformAdmin target = admins.findById(id).orElseThrow(() -> new ApiException(404, "Platform user not found"));
        target.setRole(role);
        audit.log(null, admin().getName(), "PLATFORM_ROLE_CHANGED", role, "PLATFORM_ADMIN", String.valueOf(id), "");
        return Map.of("id", target.getId(), "role", target.getRole());
    }

    @PostMapping("/businesses/{id}/grants")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BILLING_ADMIN')")
    Map<String, Object> grant(@PathVariable long id, @Valid @RequestBody Grant body) {
        return subscriptions.grant(id, body.planCode(), body.days(), body.reason(), body.source(), admin().getName());
    }

    @GetMapping("/admins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    List<Map<String, Object>> listAdmins() {
        return admins.findAll().stream().map(row -> {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("id", row.getId());
            body.put("name", row.getName());
            body.put("phone", row.getPhone());
            body.put("role", row.getRole());
            body.put("active", row.isActive());
            return body;
        }).toList();
    }

    @PostMapping("/businesses/{id}/owner-recovery")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT_ADMIN')")
    Map<String, String> ownerRecovery(@PathVariable long id) {
        return accounts.platformOwnerRecovery(id, admin().getName());
    }

    private PlatformPrincipal admin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new ApiException(401, "Platform login required");
        }
        return principal;
    }

    public record Login(@NotBlank String phone, @NotBlank String pin) {}

    public record AdminBody(@NotBlank String name, @NotBlank String phone, @NotBlank String pin, @NotBlank String role) {}

    public record PlanBody(String code, String name, String description, BigDecimal price, String currency,
                           String interval, String status, List<String> modules,
                           Integer branchLimit, Integer userLimit, Integer deviceLimit, Integer productLimit) {}

    public record Reason(@NotBlank String reason) {}

    public record Grant(@NotBlank String planCode, int days, @NotBlank String reason, String source) {}

    public record Recover(@NotBlank String phone, @NotBlank String recoveryCode, @NotBlank String newPin) {}
}
