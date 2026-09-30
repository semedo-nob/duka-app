package com.duka.service;

import com.duka.config.DukaProperties;
import com.duka.config.Phones;
import com.duka.domain.AuditEvent;
import com.duka.domain.Business;
import com.duka.domain.BusinessStatus;
import com.duka.domain.DeviceRegistration;
import com.duka.domain.SubscriptionAccount;
import com.duka.domain.SubscriptionPayment;
import com.duka.domain.SupportCase;
import com.duka.domain.PlatformAdmin;
import com.duka.domain.UserAccount;
import com.duka.domain.UserRole;
import com.duka.integrations.etims.EtimsAdapter;
import com.duka.integrations.mpesa.MpesaGateway;
import com.duka.repo.AuditEventRepository;
import com.duka.repo.BranchRepository;
import com.duka.repo.BusinessRepository;
import com.duka.repo.DeviceRegistrationRepository;
import com.duka.repo.SubscriptionAccountRepository;
import com.duka.repo.SubscriptionPaymentRepository;
import com.duka.repo.PlatformAdminRepository;
import com.duka.repo.SupportCaseRepository;
import com.duka.repo.UserAccountRepository;
import com.duka.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformService {
    private final BusinessRepository businesses;
    private final UserAccountRepository users;
    private final BranchRepository branches;
    private final DeviceRegistrationRepository devices;
    private final SubscriptionAccountRepository subscriptions;
    private final SubscriptionPaymentRepository payments;
    private final SupportCaseRepository supportCases;
    private final AuditEventRepository auditEvents;
    private final SubscriptionService subscriptionService;
    private final SupportService support;
    private final MpesaGateway mpesa;
    private final EtimsAdapter etims;
    private final DukaProperties properties;
    private final PlatformAdminRepository admins;
    private final PasswordEncoder encoder;
    private final AuditService auditLog;

    @Transactional
    public Map<String, Object> approve(long id, String reason, String actor) {
        Business business = require(id);
        if (business.getStatus() != BusinessStatus.PENDING_APPROVAL
                && business.getStatus() != BusinessStatus.PENDING_VERIFICATION
                && business.getStatus() != BusinessStatus.REGISTERED) {
            throw new ApiException(409, "This business is not waiting for approval");
        }
        BusinessStatus previous = business.getStatus();
        business.setStatus(BusinessStatus.ACTIVE);
        business.setActive(true);
        stamp(business, reason, actor);
        startTrial(business);
        auditLog.log(business.getId(), actor, "BUSINESS_APPROVED", previous.name() + " -> ACTIVE: " + reason.trim(), "BUSINESS", String.valueOf(id), "");
        return summary(business);
    }

    @Transactional
    public Map<String, Object> suspend(long id, String reason, String actor) {
        Business business = require(id);
        if (business.getStatus() == BusinessStatus.CLOSED || business.getStatus() == BusinessStatus.DEACTIVATED) {
            throw new ApiException(409, "A closed business is not suspended. Review it before any further change.");
        }
        BusinessStatus previous = business.getStatus();
        business.setStatus(BusinessStatus.SUSPENDED);
        business.setActive(false);
        stamp(business, reason, actor);
        auditLog.log(business.getId(), actor, "BUSINESS_SUSPENDED", previous.name() + " -> SUSPENDED: " + reason.trim(), "BUSINESS", String.valueOf(id), "");
        return summary(business);
    }

    @Transactional
    public Map<String, Object> reactivate(long id, String reason, String actor) {
        Business business = require(id);
        if (business.getStatus() != BusinessStatus.SUSPENDED) {
            throw new ApiException(409, "Only a suspended business can be reactivated");
        }
        business.setStatus(BusinessStatus.ACTIVE);
        business.setActive(true);
        stamp(business, reason, actor);
        auditLog.log(business.getId(), actor, "BUSINESS_REACTIVATED", "SUSPENDED -> ACTIVE: " + reason.trim(), "BUSINESS", String.valueOf(id), "");
        return summary(business);
    }

    @Transactional
    public Map<String, Object> close(long id, String reason, String actor) {
        Business business = require(id);
        BusinessStatus previous = business.getStatus();
        business.setStatus(BusinessStatus.CLOSED);
        business.setActive(false);
        business.setCancelledAt(Instant.now());
        stamp(business, reason, actor);
        auditLog.log(business.getId(), actor, "BUSINESS_CLOSED", previous.name() + " -> CLOSED: " + reason.trim(), "BUSINESS", String.valueOf(id), "");
        return summary(business);
    }

    @Transactional
    public Map<String, Object> recoverAdmin(String phone, String recoveryCode, String newPin) {
        String expected = properties.getPlatform().getRecoveryCode();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(503, "Platform recovery is not armed. Set DUKA_PLATFORM_RECOVERY_CODE on the server, use it once, then remove it.");
        }
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), recoveryCode.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(401, "Recovery code is incorrect");
        }
        if (newPin == null || newPin.length() < 8) {
            throw new ApiException(400, "The new platform PIN must be at least 8 characters");
        }
        PlatformAdmin admin = admins.findByPhone(Phones.normalize(phone))
                .orElseThrow(() -> new ApiException(404, "No platform user uses that phone"));
        admin.setPinHash(encoder.encode(newPin));
        auditLog.log(null, admin.getName(), "PLATFORM_CREDENTIAL_RESET", "Platform PIN reset with the server recovery code", "PLATFORM_ADMIN", admin.getId().toString(), "");
        return Map.of("ok", true, "phone", admin.getPhone());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> supportContext(long businessId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("business", summary(require(businessId)));
        body.put("subscription", subscriptionService.status(businessId));
        body.put("diagnostics", diagnostics(require(businessId)));
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> activity() {
        return auditEvents.findTop50ByOrderByCreatedAtDesc().stream().map(this::audit).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        List<Business> all = businesses.findAll();
        List<SubscriptionAccount> subs = subscriptions.findAll();
        List<SubscriptionPayment> paymentRows = payments.findAll();
        List<SupportCase> cases = supportCases.findAll();
        Instant recent = Instant.now().minus(1, ChronoUnit.DAYS);
        Map<String, Object> body = new LinkedHashMap<>();
        List<DeviceRegistration> deviceRows = devices.findAll();
        Instant onlineSince = Instant.now().minus(15, ChronoUnit.MINUTES);
        body.put("businesses", all.size());
        body.put("activeBusinesses", all.stream().filter(this::operating).count());
        body.put("pendingApproval", all.stream().filter(row -> row.getStatus() == BusinessStatus.PENDING_APPROVAL
                || row.getStatus() == BusinessStatus.PENDING_VERIFICATION
                || row.getStatus() == BusinessStatus.REGISTERED).count());
        body.put("suspendedBusinesses", all.stream().filter(row -> row.getStatus() == BusinessStatus.SUSPENDED).count());
        body.put("trialBusinesses", subs.stream().filter(row -> "TRIAL".equals(row.getStatus())).count());
        body.put("activeSubscriptions", subs.stream().filter(row -> "ACTIVE".equals(row.getStatus())).count());
        body.put("pastDueSubscriptions", subs.stream().filter(row -> "PAST_DUE".equals(row.getStatus())).count());
        body.put("expiredSubscriptions", subs.stream().filter(row -> "EXPIRED".equals(row.getStatus())).count());
        body.put("successfulPayments", paymentRows.stream().filter(row -> "SUCCEEDED".equals(row.getStatus())).count());
        body.put("pendingPayments", paymentRows.stream().filter(row -> "PENDING".equals(row.getStatus())).count());
        body.put("failedPayments", paymentRows.stream().filter(row -> "FAILED".equals(row.getStatus())).count());
        body.put("supportTickets", cases.size());
        body.put("openTickets", cases.stream().filter(row -> "OPEN".equals(row.getStatus())).count());
        body.put("inProgressTickets", cases.stream().filter(row -> "IN_PROGRESS".equals(row.getStatus())).count());
        body.put("waitingTickets", cases.stream().filter(row -> "WAITING_FOR_CUSTOMER".equals(row.getStatus())).count());
        body.put("unresolvedTickets", cases.stream().filter(row -> !List.of("RESOLVED", "CLOSED").contains(row.getStatus())).count());
        body.put("devicesOnline", deviceRows.stream().filter(row -> !row.isRevoked() && row.getLastSeen() != null && row.getLastSeen().isAfter(onlineSince)).count());
        body.put("devicesOffline", deviceRows.stream().filter(row -> !row.isRevoked() && (row.getLastSeen() == null || !row.getLastSeen().isAfter(onlineSince))).count());
        body.put("devicesPendingSync", deviceRows.stream().filter(row -> !row.isRevoked() && row.getPendingSync() > 0).count());
        body.put("devicesFailedSync", deviceRows.stream().filter(row -> !row.isRevoked() && row.getFailedSync() > 0).count());
        body.put("activeDevices", deviceRows.stream().filter(row -> !row.isRevoked() && row.getLastSeen() != null && row.getLastSeen().isAfter(recent)).count());
        body.put("billingProvider", properties.getBilling().getProvider());
        body.put("billingConfigured", properties.getBilling().getSecretKey() != null && !properties.getBilling().getSecretKey().isBlank()
                && "paystack".equalsIgnoreCase(properties.getBilling().getProvider()));
        body.put("recentBusinesses", all.stream()
                .sorted(Comparator.comparing(Business::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(this::summary)
                .toList());
        body.put("recentPayments", paymentRows.stream()
                .sorted(Comparator.comparing(SubscriptionPayment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(this::paymentBrief)
                .toList());
        body.put("recentTickets", cases.stream()
                .sorted(Comparator.comparing(SupportCase::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(this::ticketBrief)
                .toList());
        body.put("activity", auditEvents.findTop50ByOrderByCreatedAtDesc().stream().limit(12).map(this::audit).toList());
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> businesses() {
        return businesses.findAll().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> business(long id) {
        Business business = require(id);
        Map<String, Object> body = summary(business);
        body.put("owner", owner(id));
        body.put("staff", users.findByBusinessIdOrderByNameAsc(id).stream().map(this::staff).toList());
        body.put("branches", branches.findByBusinessIdOrderByNameAsc(id).stream().map(branch -> Map.of(
                "id", branch.getId(),
                "name", branch.getName()
        )).toList());
        body.put("devices", devices.findByBusinessId(id).stream().map(this::device).toList());
        body.put("subscription", subscriptionService.status(id));
        body.put("support", support.forBusiness(id));
        body.put("diagnostics", diagnostics(business));
        body.put("audit", auditEvents.findTop100ByBusinessIdOrderByCreatedAtDesc(id).stream().map(this::audit).toList());
        body.put("billingEvents", subscriptionService.eventsFor(id));
        Map<String, Object> diagnosis = subscriptionService.diagnose(id);
        diagnosis.put("callbackUrl", properties.getBilling().getCallbackUrl());
        diagnosis.put("webhookUrl", properties.getBilling().getWebhookUrl());
        body.put("billingDiagnosis", diagnosis);
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> diagnostics(long id) {
        return diagnostics(require(id));
    }

    private Map<String, Object> diagnostics(Business business) {
        List<DeviceRegistration> rows = devices.findByBusinessId(business.getId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("businessId", business.getId());
        body.put("businessStatus", business.getStatus().name());
        body.put("subscription", subscriptionService.status(business.getId()));
        body.put("mpesaMode", mpesa.mockMode() ? "mock" : "live");
        body.put("mpesaConfigured", !mpesa.mockMode() && properties.getMpesa().getConsumerKey() != null && !properties.getMpesa().getConsumerKey().isBlank());
        body.put("etimsMode", etims.mode());
        body.put("etimsConfigured", etims.configured());
        body.put("devices", rows.stream().map(this::device).toList());
        return body;
    }

    private Map<String, Object> summary(Business business) {
        SubscriptionAccount subscription = subscriptions.findByBusinessIdOrderByCreatedAtDesc(business.getId()).stream().findFirst().orElse(null);
        UserAccount owner = users.findByBusinessIdOrderByNameAsc(business.getId()).stream()
                .filter(user -> user.getRole() == UserRole.OWNER)
                .findFirst()
                .orElse(null);
        Instant last = devices.findByBusinessId(business.getId()).stream()
                .map(DeviceRegistration::getLastSeen)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", business.getId());
        body.put("name", business.getName());
        body.put("type", business.getBusinessType());
        body.put("status", business.getStatus().name());
        body.put("statusReason", business.getStatusReason());
        body.put("statusChangedBy", business.getStatusChangedBy());
        body.put("statusChangedAt", business.getStatusChangedAt());
        body.put("active", business.isActive());
        body.put("owner", owner == null ? "" : owner.getName());
        body.put("ownerPhone", owner == null ? "" : owner.getPhone());
        body.put("plan", subscription == null ? null : subscription.getPlanCode());
        body.put("subscriptionStatus", subscription == null ? "NONE" : subscription.getStatus());
        body.put("trial", subscription != null && "TRIAL".equals(subscription.getStatus()));
        body.put("branches", branches.findByBusinessIdOrderByNameAsc(business.getId()).size());
        body.put("users", users.findByBusinessIdOrderByNameAsc(business.getId()).size());
        body.put("devices", devices.findByBusinessId(business.getId()).size());
        body.put("createdAt", business.getCreatedAt());
        body.put("lastActivity", last);
        return body;
    }

    private Map<String, Object> paymentBrief(SubscriptionPayment payment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", payment.getId());
        body.put("businessId", payment.getBusinessId());
        body.put("plan", payment.getPlanCode());
        body.put("amount", payment.getAmount());
        body.put("currency", payment.getCurrency());
        body.put("status", payment.getStatus());
        body.put("provider", payment.getProvider());
        body.put("createdAt", payment.getCreatedAt());
        return body;
    }

    private Map<String, Object> ticketBrief(SupportCase supportCase) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", supportCase.getId());
        body.put("topic", supportCase.getTopic());
        body.put("status", supportCase.getStatus());
        body.put("category", supportCase.getCategory());
        body.put("priority", supportCase.getPriority());
        body.put("businessId", supportCase.getBusinessId());
        body.put("updatedAt", supportCase.getUpdatedAt());
        return body;
    }

    private Map<String, Object> staff(UserAccount user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId());
        body.put("name", user.getName());
        body.put("phone", user.getPhone());
        body.put("role", user.getRole().name());
        body.put("status", user.getStatus().name());
        body.put("active", user.isActive());
        return body;
    }

    private Map<String, Object> owner(long businessId) {
        return users.findByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(user -> user.getRole() == UserRole.OWNER)
                .findFirst()
                .map(this::staff)
                .orElse(Map.of());
    }

    private Map<String, Object> device(DeviceRegistration device) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", device.getDeviceId());
        body.put("installationId", device.getInstallationId());
        body.put("branchId", device.getBranchId());
        body.put("name", device.getName());
        body.put("appVersion", device.getAppVersion());
        body.put("lastSeen", device.getLastSeen());
        body.put("lastSyncAt", device.getLastSyncAt());
        body.put("pendingSync", device.getPendingSync());
        body.put("failedSync", device.getFailedSync());
        body.put("oldestPendingAt", device.getOldestPendingAt());
        body.put("lastError", device.getLastError());
        body.put("printerStatus", device.getPrinterStatus());
        body.put("scannerStatus", device.getScannerStatus());
        body.put("revoked", device.isRevoked());
        return body;
    }

    private Map<String, Object> audit(AuditEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", event.getId());
        body.put("businessId", event.getBusinessId());
        body.put("actor", event.getActor());
        body.put("action", event.getAction());
        body.put("detail", event.getDetail());
        body.put("entityType", event.getEntityType());
        body.put("entityId", event.getEntityId());
        body.put("at", event.getCreatedAt());
        return body;
    }

    private void stamp(Business business, String reason, String actor) {
        if (reason == null || reason.trim().length() < 3) {
            throw new ApiException(400, "Write a short reason. It is stored on the audit event.");
        }
        business.setStatusReason(reason.trim());
        business.setStatusChangedAt(Instant.now());
        business.setStatusChangedBy(actor);
    }

    private void startTrial(Business business) {
        boolean open = subscriptions.findByBusinessIdOrderByCreatedAtDesc(business.getId()).stream()
                .anyMatch(row -> List.of("TRIAL", "ACTIVE", "PAST_DUE", "PENDING").contains(row.getStatus()));
        if (open) {
            return;
        }
        Instant now = Instant.now();
        SubscriptionAccount trial = new SubscriptionAccount();
        trial.setBusinessId(business.getId());
        trial.setPlanCode("core");
        trial.setStatus("TRIAL");
        trial.setProvider("trial");
        trial.setProviderRef("trial-" + business.getId() + "-" + now.toEpochMilli());
        trial.setStartedAt(now);
        trial.setExpiresAt(now.plus(14, ChronoUnit.DAYS));
        subscriptions.save(trial);
        auditLog.log(business.getId(), "Platform", "SUBSCRIPTION_CHANGED", "14-day core trial started after approval", "SUBSCRIPTION", trial.getProviderRef(), "");
    }

    private boolean operating(Business business) {
        return business.isActive() && (business.getStatus() == BusinessStatus.ACTIVE
                || business.getStatus() == BusinessStatus.REACTIVATED
                || business.getStatus() == BusinessStatus.TRIAL);
    }

    private Business require(long id) {
        return businesses.findById(id).orElseThrow(() -> new ApiException(404, "Business not found"));
    }
}
