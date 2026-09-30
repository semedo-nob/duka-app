package com.duka.service;

import com.duka.domain.BillingEvent;
import com.duka.domain.Business;
import com.duka.domain.BusinessStatus;
import com.duka.domain.Plan;
import com.duka.domain.PlanModule;
import com.duka.domain.SubscriptionAccount;
import com.duka.domain.SubscriptionPayment;
import com.duka.integrations.billing.PaystackBillingProvider;
import com.duka.integrations.billing.PaystackSignatures;
import com.duka.repo.BillingEventRepository;
import com.duka.repo.BusinessRepository;
import com.duka.repo.PlanModuleRepository;
import com.duka.repo.PlanRepository;
import com.duka.repo.SubscriptionAccountRepository;
import com.duka.repo.SubscriptionPaymentRepository;
import com.duka.security.UserPrincipal;
import com.duka.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {
    public static final List<String> MODULES = List.of("purchasing", "credit", "advReports", "multiBranch", "etims");

    private final SubscriptionAccountRepository subscriptions;
    private final SubscriptionPaymentRepository payments;
    private final BillingEventRepository billingEvents;
    private final PlanRepository plans;
    private final PlanModuleRepository modules;
    private final BusinessRepository businesses;
    private final PaystackBillingProvider paystack;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Environment environment;

    @Transactional(readOnly = true)
    public Map<String, Object> status(Long businessId) {
        SubscriptionAccount current = display(businessId);
        Map<String, Boolean> entitlements = entitlements(businessId, entitledAccount(businessId));
        Plan plan = current == null ? null : plans.findById(current.getPlanCode()).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("providerConfigured", paystack.configured());
        body.put("provider", paystack.provider());
        body.put("status", current == null ? "NONE" : current.getStatus());
        body.put("plan", current == null ? null : current.getPlanCode());
        body.put("planName", plan == null ? null : plan.getName());
        body.put("startedAt", current == null ? null : current.getStartedAt());
        body.put("renewsAt", current == null ? null : current.getRenewsAt());
        body.put("expiresAt", current == null ? null : current.getExpiresAt());
        body.put("graceUntil", current == null ? null : current.getGraceUntil());
        body.put("activationSource", activationSource(current));
        body.put("modules", entitlements);
        body.put("payments", businessId == null ? List.of() : payments.findByBusinessIdOrderByCreatedAtDesc(businessId).stream().map(this::paymentView).toList());
        body.put("message", customerMessage(current));
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> publicPlans() {
        return plans.findAll().stream().filter(plan -> "ACTIVE".equals(plan.getStatus())).map(this::planView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> allPlans() {
        return plans.findAll().stream().map(this::planView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> statusRows() {
        return subscriptions.findAll().stream().map(row -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", row.getId());
            body.put("businessId", row.getBusinessId());
            body.put("plan", row.getPlanCode());
            body.put("status", row.getStatus());
            body.put("provider", row.getProvider());
            body.put("activationSource", activationSource(row));
            body.put("reference", row.getProviderRef());
            body.put("startedAt", row.getStartedAt());
            body.put("renewsAt", row.getRenewsAt());
            body.put("expiresAt", row.getExpiresAt());
            body.put("graceUntil", row.getGraceUntil());
            return body;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> entitlements(Long businessId) {
        return entitlements(businessId, entitledAccount(businessId));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> checkout(String planCode, String email, String channel, String phone, UserPrincipal user) {
        Plan plan = requireActivePlan(planCode);
        if (plan.getPriceAmount().signum() == 0) {
            activateWithoutCharge(user.getBusinessId(), plan, "moved to the free " + plan.getName() + " plan");
            return status(user.getBusinessId());
        }
        if ("mock".equalsIgnoreCase(paystack.provider())) {
            assertMockAllowed();
            return mockCheckout(user.getBusinessId(), plan);
        }
        if (!paystack.configured()) {
            throw new ApiException(503, paystack.configurationBlocker());
        }
        Business business = businesses.findById(user.getBusinessId()).orElseThrow(() -> new ApiException(404, "Business not found"));
        String chargeEmail = email == null || email.isBlank() ? business.getEmail() : email.trim();
        if (chargeEmail == null || !chargeEmail.contains("@")) {
            throw new ApiException(400, "Add an email address before checkout. Paystack requires one, and Duka will not invent a payment.");
        }
        business.setEmail(chargeEmail);
        String reference = "duka_" + business.getId() + "_" + plan.getCode() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.setBusinessId(business.getId());
        payment.setPlanCode(plan.getCode());
        payment.setAmount(plan.getPriceAmount());
        payment.setCurrency(plan.getCurrency());
        payment.setStatus("PENDING");
        payment.setProvider("paystack");
        payment.setProviderRef(reference);
        payments.save(payment);
        SubscriptionAccount pending = new SubscriptionAccount();
        pending.setBusinessId(business.getId());
        pending.setPlanCode(plan.getCode());
        pending.setStatus("PENDING");
        pending.setProvider("paystack");
        pending.setProviderRef(reference);
        pending.setStartedAt(Instant.now());
        subscriptions.save(pending);
        payment.setSubscriptionId(pending.getId());
        audit.log(business.getId(), user.getName(), "SUBSCRIPTION_CHANGED", "checkout started for " + plan.getCode() + " ref " + reference, "SUBSCRIPTION", reference, "");
        recordEvent("init-" + reference, "PAYMENT_INITIALIZED", business.getId(), reference, "checkout started for " + plan.getCode(), true);
        boolean mpesa = "mpesa".equalsIgnoreCase(channel);
        try {
            if (mpesa) {
                paystack.chargeMpesa(chargeEmail, plan.getPriceAmount(), plan.getCurrency(), reference, phone, business.getId(), plan.getCode());
                return confirmFromPaystack(reference, user.getBusinessId(),
                        "Paystack sent the M-Pesa prompt. The module stays locked until Paystack verifies the charge.");
            }
            Map<String, Object> session = paystack.initialize(chargeEmail, plan.getPriceAmount(), plan.getCurrency(), reference, business.getId(), plan.getCode());
            session.put("subscriptionStatus", "PENDING");
            session.put("message", "Complete payment on Paystack. Duka unlocks the module only after it verifies the charge.");
            return session;
        } catch (RuntimeException ex) {
            payment.setStatus("FAILED");
            payment.setFailureReason(ex.getMessage() == null ? "checkout failed" : clip(ex.getMessage()));
            pending.setStatus("FAILED");
            audit.log(business.getId(), "Billing", "PAYMENT_FAILED", reference, "PAYMENT", reference, "");
            throw ex;
        }
    }

    @Transactional
    public Map<String, Object> verify(String reference, UserPrincipal user) {
        if (reference == null || reference.isBlank()) {
            throw new ApiException(400, "Payment reference is required");
        }
        SubscriptionPayment payment = payments.findByProviderAndProviderRef("paystack", reference.trim())
                .orElseThrow(() -> new ApiException(404, "No checkout with that reference for this business"));
        if (!user.getBusinessId().equals(payment.getBusinessId())) {
            throw new ApiException(404, "No checkout with that reference for this business");
        }
        return confirmFromPaystack(reference.trim(), user.getBusinessId(),
                "Payment is still processing. We're waiting for confirmation from Paystack. The feature unlocks only after verification.");
    }

    @Transactional
    public Map<String, Object> cancel(UserPrincipal user) {
        SubscriptionAccount current = entitledAccount(user.getBusinessId());
        if (current == null) {
            current = display(user.getBusinessId());
        }
        if (current == null || !List.of("ACTIVE", "PAST_DUE", "TRIAL").contains(current.getStatus())) {
            throw new ApiException(400, "There is no subscription to cancel");
        }
        current.setStatus("CANCELLED");
        current.setCancelledAt(Instant.now());
        audit.log(user.getBusinessId(), user.getName(), "SUBSCRIPTION_CHANGED", "cancelled " + current.getPlanCode(), "SUBSCRIPTION", String.valueOf(current.getId()), "");
        audit.log(user.getBusinessId(), user.getName(), "ENTITLEMENT_CHANGED", "paid modules locked after cancellation", "SUBSCRIPTION", String.valueOf(current.getId()), "");
        return status(user.getBusinessId());
    }

    @Transactional
    public Map<String, Object> restore(UserPrincipal user) {
        SubscriptionAccount current = subscriptions.findByBusinessIdOrderByCreatedAtDesc(user.getBusinessId()).stream()
                .filter(row -> "CANCELLED".equals(row.getStatus()))
                .findFirst()
                .orElseThrow(() -> new ApiException(400, "There is no cancelled subscription to restore"));
        if (current.getExpiresAt() == null || !current.getExpiresAt().isAfter(Instant.now())) {
            throw new ApiException(409, "That subscription has expired. Start checkout again. Duka will not restore access without a verified payment.");
        }
        current.setStatus("ACTIVE");
        current.setCancelledAt(null);
        audit.log(user.getBusinessId(), user.getName(), "SUBSCRIPTION_CHANGED", "restored " + current.getPlanCode(), "SUBSCRIPTION", String.valueOf(current.getId()), "");
        audit.log(user.getBusinessId(), user.getName(), "ENTITLEMENT_CHANGED", "modules restored for " + current.getPlanCode(), "SUBSCRIPTION", String.valueOf(current.getId()), "");
        return status(user.getBusinessId());
    }

    @Transactional
    public Map<String, Object> grant(long businessId, String planCode, int days, String reason, String source, String actor) {
        if (reason == null || reason.trim().length() < 8) {
            throw new ApiException(400, "Write why this plan is being activated. The reason is stored on the audit event.");
        }
        if (days < 1 || days > 366) {
            throw new ApiException(400, "Duration must be between 1 and 366 days");
        }
        String activation = source == null || source.isBlank() ? "ADMIN_GRANT" : source.trim().toUpperCase();
        if (!List.of("ADMIN_GRANT", "PROMOTIONAL").contains(activation)) {
            throw new ApiException(400, "Activation source must be ADMIN_GRANT or PROMOTIONAL");
        }
        Business business = businesses.findById(businessId).orElseThrow(() -> new ApiException(404, "Business not found"));
        Plan plan = requireActivePlan(planCode);
        Instant now = Instant.now();
        Instant expires = now.plus(days, ChronoUnit.DAYS);
        String reference = activation.toLowerCase() + "-" + business.getId() + "-" + now.toEpochMilli();
        SubscriptionAccount account = new SubscriptionAccount();
        account.setBusinessId(business.getId());
        account.setPlanCode(plan.getCode());
        account.setStatus("ACTIVE");
        account.setProvider(activation);
        account.setProviderRef(reference);
        account.setStartedAt(now);
        account.setRenewsAt(expires);
        account.setExpiresAt(expires);
        account.setGraceUntil(expires.plus(7, ChronoUnit.DAYS));
        subscriptions.save(account);
        BillingEvent event = new BillingEvent();
        event.setProvider(activation);
        event.setEventId(reference);
        event.setEventType(activation);
        event.setBusinessId(business.getId());
        event.setProviderRef(reference);
        event.setSummary(actor + " granted " + plan.getCode() + " for " + days + " days. " + reason.trim());
        event.setSignatureValid(false);
        event.setProcessed(true);
        billingEvents.save(event);
        if (business.getStatus() == BusinessStatus.TRIAL || business.getStatus() == BusinessStatus.SUBSCRIPTION_EXPIRED) {
            business.setStatus(BusinessStatus.ACTIVE);
            business.setActive(true);
        }
        String detail = "activationType=" + activation
                + " operator=" + actor
                + " plan=" + plan.getCode()
                + " start=" + now
                + " expiry=" + expires
                + " reason=" + reason.trim();
        audit.log(business.getId(), actor, "SUBSCRIPTION_CHANGED", detail, "SUBSCRIPTION", reference, "");
        audit.log(business.getId(), actor, "ENTITLEMENT_CHANGED",
                activation + " " + String.join(",", modules.findByPlanCode(plan.getCode()).stream().map(PlanModule::getModuleKey).toList()),
                "SUBSCRIPTION", reference, "");
        Map<String, Object> body = status(business.getId());
        body.put("activationType", activation);
        body.put("reference", reference);
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> diagnose(long businessId) {
        SubscriptionAccount current = display(businessId);
        List<SubscriptionPayment> rows = payments.findByBusinessIdOrderByCreatedAtDesc(businessId);
        List<BillingEvent> events = billingEvents.findByBusinessIdOrderByCreatedAtDesc(businessId);
        SubscriptionPayment latest = rows.isEmpty() ? null : rows.get(0);
        long paystackWebhooks = events.stream().filter(event -> "paystack".equals(event.getProvider())
                && (event.getEventType().startsWith("charge.") || "WEBHOOK_VERIFIED".equals(event.getEventType()) || "WEBHOOK_RECEIVED".equals(event.getEventType()))).count();
        long rejected = events.stream().filter(event -> "paystack".equals(event.getProvider()) && !event.isSignatureValid()).count();
        boolean anyUnlocked = entitlements(businessId).values().stream().anyMatch(Boolean::booleanValue);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subscriptionStatus", current == null ? "NONE" : current.getStatus());
        body.put("plan", current == null ? null : current.getPlanCode());
        body.put("activationSource", activationSource(current));
        body.put("provider", current == null ? null : current.getProvider());
        body.put("reference", current == null ? null : current.getProviderRef());
        body.put("startedAt", current == null ? null : current.getStartedAt());
        body.put("expiresAt", current == null ? null : current.getExpiresAt());
        body.put("renewsAt", current == null ? null : current.getRenewsAt());
        body.put("billingProvider", paystack.provider());
        body.put("providerConfigured", paystack.configured());
        body.put("entitlements", entitlements(businessId));
        body.put("latestPayment", latest == null ? null : paymentView(latest));
        body.put("paymentCount", rows.size());
        body.put("webhookCount", paystackWebhooks);
        body.put("rejectedWebhooks", rejected);
        body.put("likelyCause", likelyCause(current, latest, paystackWebhooks, rejected, anyUnlocked));
        body.put("events", events.stream().limit(12).map(this::eventView).toList());
        return body;
    }

    @Transactional
    public Map<String, Object> handleWebhook(String rawBody, String signature) {
        if (!paystack.configured() || paystack.webhookSecret().isBlank()) {
            throw new ApiException(401, "Billing webhook secret is not configured");
        }
        if (!PaystackSignatures.matches(paystack.webhookSecret(), rawBody, signature)) {
            throw new ApiException(401, "Billing webhook signature is missing or incorrect");
        }
        JsonNode tree;
        try {
            tree = json.readTree(rawBody);
        } catch (Exception ex) {
            throw new ApiException(400, "Billing webhook body is not JSON");
        }
        String eventType = tree.path("event").asText("");
        JsonNode data = tree.path("data");
        String eventId = data.path("id").asText("");
        if (eventId.isBlank()) {
            eventId = data.path("reference").asText("");
        }
        if (eventId.isBlank()) {
            throw new ApiException(400, "Billing webhook has no event id");
        }
        if (billingEvents.findByProviderAndEventId("paystack", eventId).isPresent()) {
            return Map.of("duplicate", true, "eventId", eventId);
        }
        String reference = data.path("reference").asText("");
        Long businessId = readBusinessId(data);
        String planCode = data.path("metadata").path("planCode").asText("");
        BillingEvent stored = new BillingEvent();
        stored.setProvider("paystack");
        stored.setEventId(eventId);
        stored.setEventType(eventType.isBlank() ? "unknown" : eventType);
        stored.setBusinessId(businessId);
        stored.setProviderRef(reference);
        stored.setSignatureValid(true);
        stored.setSummary(eventType + " " + reference);
        try {
            billingEvents.saveAndFlush(stored);
        } catch (DataIntegrityViolationException ex) {
            return Map.of("duplicate", true, "eventId", eventId);
        }
        recordEvent("webhook-" + eventId, "WEBHOOK_VERIFIED", businessId, reference, eventType, false);
        if ("charge.success".equals(eventType)) {
            applySuccess(businessId, planCode, reference, data.path("currency").asText("KES"), data.path("gateway_response").asText(""));
            stored.setProcessed(true);
            recordEvent("payment-success-" + reference, "PAYMENT_SUCCESS", businessId, reference, "charge.success", true);
            recordEvent("entitlements-" + reference, "ENTITLEMENTS_REFRESHED", businessId, reference, planCode, true);
            audit.log(businessId, "Billing", "PAYMENT_VERIFIED", reference, "PAYMENT", reference, "");
        } else if ("charge.failed".equals(eventType)) {
            applyFailure(businessId, planCode, reference, data.path("gateway_response").asText("Paystack reported a failed charge"));
            stored.setProcessed(true);
            recordEvent("payment-failed-" + reference, "PAYMENT_FAILED", businessId, reference, "charge.failed", true);
            audit.log(businessId, "Billing", "PAYMENT_FAILED", reference, "PAYMENT", reference, "");
        } else if ("refund.processed".equals(eventType)) {
            applyRefund(businessId, reference);
            stored.setProcessed(true);
        }
        return Map.of("accepted", true, "eventId", eventId, "duplicate", false);
    }

    @Transactional
    public int advanceLifecycle() {
        int changed = 0;
        Instant now = Instant.now();
        for (SubscriptionAccount account : subscriptions.findAll()) {
            if ("TRIAL".equals(account.getStatus()) && account.getExpiresAt() != null && account.getExpiresAt().isBefore(now)) {
                account.setStatus("EXPIRED");
                markBusinessExpired(account.getBusinessId());
                audit.log(account.getBusinessId(), "System", "SUBSCRIPTION_CHANGED", "trial expired", "SUBSCRIPTION", String.valueOf(account.getId()), "");
                changed++;
            } else if ("ACTIVE".equals(account.getStatus()) && account.getRenewsAt() != null && account.getRenewsAt().isBefore(now)) {
                account.setStatus("PAST_DUE");
                if (account.getGraceUntil() == null || account.getGraceUntil().isBefore(now)) {
                    account.setGraceUntil(now.plus(7, ChronoUnit.DAYS));
                }
                audit.log(account.getBusinessId(), "System", "SUBSCRIPTION_CHANGED", "renewal past due", "SUBSCRIPTION", String.valueOf(account.getId()), "");
                changed++;
            } else if ("PAST_DUE".equals(account.getStatus()) && account.getGraceUntil() != null && account.getGraceUntil().isBefore(now)) {
                account.setStatus("EXPIRED");
                markBusinessExpired(account.getBusinessId());
                audit.log(account.getBusinessId(), "System", "ENTITLEMENT_CHANGED", "grace ended", "SUBSCRIPTION", String.valueOf(account.getId()), "");
                changed++;
            }
        }
        return changed;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> allPayments() {
        return payments.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::paymentView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> allEvents() {
        return billingEvents.findTop100ByOrderByCreatedAtDesc().stream().map(this::eventView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> eventsFor(Long businessId) {
        return billingEvents.findByBusinessIdOrderByCreatedAtDesc(businessId).stream().map(this::eventView).toList();
    }

    @Transactional
    public void assertCapacity(Long businessId, String kind, long currentCount) {
        SubscriptionAccount account = display(businessId);
        if (account == null || (!"ACTIVE".equals(account.getStatus()) && !"TRIAL".equals(account.getStatus()) && !inGrace(account))) {
            return;
        }
        Plan plan = plans.findById(account.getPlanCode()).orElse(null);
        if (plan == null) {
            return;
        }
        Integer limit = switch (kind) {
            case "branches" -> plan.getBranchLimit();
            case "users" -> plan.getUserLimit();
            case "devices" -> plan.getDeviceLimit();
            case "products" -> plan.getProductLimit();
            default -> null;
        };
        if (limit != null && currentCount >= limit) {
            throw new ApiException(403, "This plan allows " + limit + " " + kind + ". Choose a plan with a higher limit.");
        }
    }

    public Map<String, Object> savePlan(String code, String name, String description, BigDecimal price, String currency,
                                        String interval, String status, List<String> moduleKeys,
                                        Integer branchLimit, Integer userLimit, Integer deviceLimit, Integer productLimit,
                                        boolean creating, String actor) {
        if (code == null || !code.matches("[a-z0-9]{2,32}")) {
            throw new ApiException(400, "Plan code must be 2-32 lowercase letters or digits");
        }
        Plan plan = plans.findById(code).orElseGet(Plan::new);
        if (creating && plan.getCode() != null) {
            throw new ApiException(409, "That plan already exists");
        }
        if (!creating && plan.getCode() == null) {
            throw new ApiException(404, "Plan not found");
        }
        plan.setCode(code);
        plan.setName(name == null || name.isBlank() ? code : name.trim());
        plan.setDescription(description == null ? "" : description.trim());
        plan.setPriceAmount(price == null ? BigDecimal.ZERO : price);
        plan.setCurrency(currency == null || currency.isBlank() ? "KES" : currency.trim().toUpperCase());
        plan.setBillingInterval(interval == null || interval.isBlank() ? "MONTH" : interval.trim().toUpperCase());
        plan.setStatus(status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase());
        if ("ARCHIVED".equals(plan.getStatus())) {
            plan.setStatus("INACTIVE");
        }
        if (!List.of("MONTH", "YEAR").contains(plan.getBillingInterval()) || !List.of("ACTIVE", "INACTIVE").contains(plan.getStatus())) {
            throw new ApiException(400, "Interval must be MONTH or YEAR and status ACTIVE or INACTIVE");
        }
        if (plan.getPriceAmount().signum() < 0) {
            throw new ApiException(400, "Price cannot be negative");
        }
        plan.setBranchLimit(nonNegative(branchLimit, "Branch limit"));
        plan.setUserLimit(nonNegative(userLimit, "User limit"));
        plan.setDeviceLimit(nonNegative(deviceLimit, "Device limit"));
        plan.setProductLimit(nonNegative(productLimit, "Product limit"));
        plans.save(plan);
        modules.deleteByPlanCode(code);
        modules.flush();
        if (moduleKeys != null) {
            for (String key : moduleKeys) {
                if (!MODULES.contains(key)) {
                    throw new ApiException(400, "Unknown module " + key);
                }
                PlanModule module = new PlanModule();
                module.setPlanCode(code);
                module.setModuleKey(key);
                modules.save(module);
            }
        }
        audit.log(null, actor, creating ? "PLAN_CREATED" : "PLAN_UPDATED", code, "PLAN", code, "");
        return planView(plan);
    }

    private Map<String, Object> confirmFromPaystack(String reference, Long businessId, String pendingMessage) {
        JsonNode tree = paystack.verify(reference);
        JsonNode data = tree.path("data");
        String gatewayStatus = data.path("status").asText("");
        Long metaBusiness = readBusinessId(data);
        if (metaBusiness != null && !metaBusiness.equals(businessId)) {
            throw new ApiException(409, "Paystack says this payment belongs to a different business. Nothing was unlocked.");
        }
        String planCode = data.path("metadata").path("planCode").asText("");
        recordEvent("verify-" + reference + "-" + gatewayStatus, "WEBHOOK_RECEIVED", businessId, reference, "verify " + gatewayStatus, true);
        if ("success".equalsIgnoreCase(gatewayStatus)) {
            applySuccess(businessId, planCode, reference, data.path("currency").asText("KES"), data.path("gateway_response").asText(""));
            recordEvent("payment-success-" + reference, "PAYMENT_SUCCESS", businessId, reference, "verified", true);
            recordEvent("entitlements-" + reference, "ENTITLEMENTS_REFRESHED", businessId, reference, planCode, true);
            Map<String, Object> body = status(businessId);
            body.put("reference", reference);
            body.put("message", "Paystack verified the payment. Paid modules follow the subscription on the server.");
            return body;
        }
        if ("failed".equalsIgnoreCase(gatewayStatus) || "abandoned".equalsIgnoreCase(gatewayStatus)) {
            applyFailure(businessId, planCode, reference, data.path("gateway_response").asText("Paystack reported " + gatewayStatus));
            recordEvent("payment-failed-" + reference, "PAYMENT_FAILED", businessId, reference, gatewayStatus, true);
            Map<String, Object> body = status(businessId);
            body.put("reference", reference);
            body.put("message", "Paystack did not confirm this payment. The module stays locked.");
            return body;
        }
        Map<String, Object> body = status(businessId);
        body.put("reference", reference);
        body.put("subscriptionStatus", "PENDING");
        body.put("message", pendingMessage);
        return body;
    }

    private void applySuccess(Long businessId, String planCode, String reference, String currency, String failureIgnored) {
        if (businessId == null || planCode == null || planCode.isBlank()) {
            throw new ApiException(400, "Successful charge is missing the business or plan");
        }
        Plan plan = plans.findById(planCode).orElseThrow(() -> new ApiException(400, "Unknown plan on charge"));
        payments.findByProviderAndProviderRef("paystack", reference).ifPresent(existing -> {
            if (existing.getBusinessId() != null && !existing.getBusinessId().equals(businessId)) {
                throw new ApiException(409, "Payment reference belongs to a different business");
            }
        });
        SubscriptionPayment payment = payments.findByProviderAndProviderRef("paystack", reference).orElseGet(SubscriptionPayment::new);
        if ("SUCCEEDED".equals(payment.getStatus())) {
            return;
        }
        payment.setBusinessId(businessId);
        payment.setPlanCode(plan.getCode());
        payment.setAmount(plan.getPriceAmount());
        payment.setCurrency(currency == null || currency.isBlank() ? plan.getCurrency() : currency);
        payment.setProvider("paystack");
        payment.setProviderRef(reference);
        payment.setStatus("SUCCEEDED");
        payment.setVerifiedAt(Instant.now());
        SubscriptionAccount account = subscriptions.findByProviderAndProviderRef("paystack", reference).orElseGet(SubscriptionAccount::new);
        Instant now = Instant.now();
        Instant renews = "YEAR".equals(plan.getBillingInterval()) ? now.plus(365, ChronoUnit.DAYS) : now.plus(30, ChronoUnit.DAYS);
        if ("ACTIVE".equals(account.getStatus()) && account.getRenewsAt() != null && account.getRenewsAt().isAfter(now)) {
            renews = "YEAR".equals(plan.getBillingInterval()) ? account.getRenewsAt().plus(365, ChronoUnit.DAYS) : account.getRenewsAt().plus(30, ChronoUnit.DAYS);
        }
        account.setBusinessId(businessId);
        account.setPlanCode(plan.getCode());
        account.setStatus("ACTIVE");
        account.setProvider("paystack");
        account.setProviderRef(reference);
        if (account.getStartedAt() == null) {
            account.setStartedAt(now);
        }
        account.setRenewsAt(renews);
        account.setExpiresAt(renews);
        account.setGraceUntil(renews.plus(7, ChronoUnit.DAYS));
        account.setCancelledAt(null);
        subscriptions.save(account);
        payment.setSubscriptionId(account.getId());
        payments.save(payment);
        businesses.findById(businessId).ifPresent(business -> {
            if (business.getStatus() == BusinessStatus.TRIAL || business.getStatus() == BusinessStatus.SUBSCRIPTION_EXPIRED) {
                business.setStatus(BusinessStatus.ACTIVE);
                business.setActive(true);
            }
        });
        recordEvent("subscription-active-" + reference, "SUBSCRIPTION_ACTIVATED", businessId, reference, plan.getCode(), true);
        audit.log(businessId, "Billing", "SUBSCRIPTION_CHANGED", "activated " + plan.getCode(), "SUBSCRIPTION", reference, "");
        audit.log(businessId, "Billing", "ENTITLEMENT_CHANGED", String.join(",", modules.findByPlanCode(plan.getCode()).stream().map(PlanModule::getModuleKey).toList()), "SUBSCRIPTION", reference, "");
    }

    private void applyFailure(Long businessId, String planCode, String reference, String reason) {
        SubscriptionPayment payment = payments.findByProviderAndProviderRef("paystack", reference).orElseGet(SubscriptionPayment::new);
        if (payment.getBusinessId() == null) {
            if (businessId == null) {
                throw new ApiException(400, "Failed charge is missing the business");
            }
            payment.setBusinessId(businessId);
        }
        payment.setPlanCode(planCode == null || planCode.isBlank() ? payment.getPlanCode() == null ? "unknown" : payment.getPlanCode() : planCode);
        if (payment.getAmount() == null) {
            payment.setAmount(BigDecimal.ZERO);
        }
        payment.setProvider("paystack");
        payment.setProviderRef(reference);
        payment.setStatus("FAILED");
        payment.setFailureReason(reason == null ? "" : clip(reason));
        payments.save(payment);
        subscriptions.findByProviderAndProviderRef("paystack", reference).ifPresent(account -> {
            if (!"ACTIVE".equals(account.getStatus())) {
                account.setStatus("FAILED");
            } else {
                account.setStatus("PAST_DUE");
                if (account.getGraceUntil() == null) {
                    account.setGraceUntil(Instant.now().plus(7, ChronoUnit.DAYS));
                }
            }
        });
    }

    private Map<String, Object> mockCheckout(Long businessId, Plan plan) {
        Instant now = Instant.now();
        String reference = "mock-" + businessId + "-" + now.toEpochMilli();
        SubscriptionAccount account = new SubscriptionAccount();
        account.setBusinessId(businessId);
        account.setPlanCode(plan.getCode());
        account.setStatus("ACTIVE");
        account.setProvider("mock");
        account.setProviderRef(reference);
        account.setStartedAt(now);
        Instant renews = "YEAR".equals(plan.getBillingInterval()) ? now.plus(365, ChronoUnit.DAYS) : now.plus(30, ChronoUnit.DAYS);
        account.setRenewsAt(renews);
        account.setExpiresAt(renews);
        account.setGraceUntil(renews.plus(7, ChronoUnit.DAYS));
        subscriptions.save(account);
        BillingEvent event = new BillingEvent();
        event.setProvider("mock");
        event.setEventId(reference);
        event.setEventType("MOCK_CHECKOUT");
        event.setBusinessId(businessId);
        event.setProviderRef(reference);
        event.setSummary("Development mock checkout for " + plan.getCode() + ". This is not a Paystack payment.");
        event.setSignatureValid(false);
        event.setProcessed(true);
        billingEvents.save(event);
        audit.log(businessId, "Billing", "SUBSCRIPTION_CHANGED", "development mock checkout for " + plan.getCode() + ". Not a Paystack payment.", "SUBSCRIPTION", reference, "");
        Map<String, Object> body = status(businessId);
        body.put("reference", reference);
        body.put("message", "Development mock billing activated this plan. Production will not do this.");
        return body;
    }

    private void assertMockAllowed() {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new ApiException(503, "Production will not simulate a successful payment. Set DUKA_BILLING_PROVIDER=paystack and supply DUKA_BILLING_SECRET_KEY.");
        }
    }

    private void activateWithoutCharge(Long businessId, Plan plan, String detail) {
        Instant now = Instant.now();
        SubscriptionAccount account = new SubscriptionAccount();
        account.setBusinessId(businessId);
        account.setPlanCode(plan.getCode());
        account.setStatus("ACTIVE");
        account.setProvider("none");
        account.setProviderRef("free-" + businessId + "-" + now.toEpochMilli());
        account.setStartedAt(now);
        account.setRenewsAt(now.plus(30, ChronoUnit.DAYS));
        account.setExpiresAt(now.plus(30, ChronoUnit.DAYS));
        account.setGraceUntil(now.plus(37, ChronoUnit.DAYS));
        subscriptions.save(account);
        audit.log(businessId, "Billing", "SUBSCRIPTION_CHANGED", detail, "SUBSCRIPTION", account.getProviderRef(), "");
        audit.log(businessId, "Billing", "ENTITLEMENT_CHANGED", "free plan " + plan.getCode(), "SUBSCRIPTION", account.getProviderRef(), "");
    }

    private void markBusinessExpired(Long businessId) {
        businesses.findById(businessId).ifPresent(business -> {
            if (business.getStatus() == BusinessStatus.TRIAL || business.getStatus() == BusinessStatus.ACTIVE || business.getStatus() == BusinessStatus.REACTIVATED) {
                business.setStatus(BusinessStatus.SUBSCRIPTION_EXPIRED);
            }
        });
    }

    private String customerMessage(SubscriptionAccount current) {
        if ("mock".equalsIgnoreCase(paystack.provider())) {
            return "Development mock billing is on. A mock checkout is not a Paystack payment, and production refuses this mode.";
        }
        if (current != null && "ADMIN_GRANT".equals(current.getProvider())) {
            return "This plan was activated by Duka. It is not a Paystack payment.";
        }
        if (current != null && "PROMOTIONAL".equals(current.getProvider())) {
            return "This plan was granted as a promotion. It is not a Paystack payment.";
        }
        if (paystack.configured()) {
            return "A module unlocks only after Paystack signs a successful charge. Opening checkout does not unlock it.";
        }
        return paystack.configurationBlocker();
    }

    private String activationSource(SubscriptionAccount account) {
        if (account == null) {
            return "NONE";
        }
        if ("TRIAL".equals(account.getStatus()) || "trial".equals(account.getProvider())) {
            return "TRIAL";
        }
        return switch (account.getProvider() == null ? "" : account.getProvider()) {
            case "paystack" -> "PAYSTACK_PAYMENT";
            case "ADMIN_GRANT" -> "ADMIN_GRANT";
            case "PROMOTIONAL" -> "PROMOTIONAL";
            case "mock" -> "MOCK";
            case "none" -> "FREE";
            default -> account.getProvider();
        };
    }

    private String likelyCause(SubscriptionAccount current, SubscriptionPayment latest, long webhooks, long rejected, boolean anyUnlocked) {
        if (rejected > 0) {
            return "A Paystack webhook was rejected. Check that DUKA_BILLING_WEBHOOK_SECRET matches the key Paystack uses to sign events.";
        }
        if (latest != null && "PENDING".equals(latest.getStatus()) && webhooks == 0) {
            return "Checkout started, but Paystack has not confirmed the charge. The webhook may not have reached Duka, or the customer has not finished paying. Ask them to return to billing, or verify the reference.";
        }
        if (latest != null && "FAILED".equals(latest.getStatus())) {
            return "Paystack reported a failed charge. The module stays locked. " + (latest.getFailureReason() == null ? "" : latest.getFailureReason());
        }
        if (latest != null && "SUCCEEDED".equals(latest.getStatus()) && (current == null || !"ACTIVE".equals(current.getStatus()) && !"PAST_DUE".equals(current.getStatus()))) {
            return "The payment is marked successful, but the subscription is not active. Review the billing events for SUBSCRIPTION_ACTIVATED.";
        }
        if (current != null && "ACTIVE".equals(current.getStatus()) && !anyUnlocked) {
            return "The subscription is active on a plan with no paid modules, or the requested module is not on this plan.";
        }
        if (current != null && ("ACTIVE".equals(current.getStatus()) || "PAST_DUE".equals(current.getStatus())) && anyUnlocked) {
            return "The server has unlocked the paid modules. If the till still shows a lock, the till is showing an old copy. Ask the customer to open Billing again.";
        }
        if (!paystack.configured() && (latest == null || latest.getProvider() == null || "paystack".equals(latest.getProvider()))) {
            return "Paystack is not configured on this server, so a customer checkout cannot be verified. An admin grant is a separate record and does not create a Paystack payment.";
        }
        return "No verified payment is on file for a paid plan. Trial and core selling do not unlock paid modules.";
    }

    private SubscriptionAccount entitledAccount(Long businessId) {
        if (businessId == null) {
            return null;
        }
        return subscriptions.findByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .filter(row -> "ACTIVE".equals(row.getStatus()) || inGrace(row))
                .findFirst()
                .orElse(null);
    }

    private SubscriptionAccount display(Long businessId) {
        if (businessId == null) {
            return null;
        }
        List<SubscriptionAccount> rows = subscriptions.findByBusinessIdOrderByCreatedAtDesc(businessId);
        return rows.stream().filter(row -> "ACTIVE".equals(row.getStatus()) || inGrace(row)).findFirst()
                .or(() -> rows.stream().filter(row -> "PENDING".equals(row.getStatus()) || "FAILED".equals(row.getStatus())).findFirst())
                .or(() -> rows.stream().filter(row -> "TRIAL".equals(row.getStatus())).findFirst())
                .or(() -> rows.stream().findFirst())
                .orElse(null);
    }

    private Map<String, Boolean> entitlements(Long businessId, SubscriptionAccount current) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String module : MODULES) {
            result.put(module, false);
        }
        if (current != null && ("ACTIVE".equals(current.getStatus()) || inGrace(current))) {
            for (PlanModule module : modules.findByPlanCode(current.getPlanCode())) {
                result.put(module.getModuleKey(), true);
            }
        }
        return result;
    }

    private boolean inGrace(SubscriptionAccount account) {
        return "PAST_DUE".equals(account.getStatus())
                && account.getGraceUntil() != null
                && account.getGraceUntil().isAfter(Instant.now());
    }

    private Plan requireActivePlan(String planCode) {
        Plan plan = plans.findById(planCode == null ? "" : planCode).orElseThrow(() -> new ApiException(400, "Unknown plan"));
        if (!"ACTIVE".equals(plan.getStatus())) {
            throw new ApiException(400, "That plan is not available");
        }
        return plan;
    }

    private Long readBusinessId(JsonNode data) {
        JsonNode value = data.path("metadata").path("businessId");
        if (value.isNumber()) {
            return value.asLong();
        }
        if (value.isTextual() && !value.asText().isBlank()) {
            return Long.parseLong(value.asText());
        }
        return null;
    }

    private Map<String, Object> planView(Plan plan) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", plan.getCode());
        body.put("name", plan.getName());
        body.put("description", plan.getDescription());
        body.put("price", plan.getPriceAmount());
        body.put("currency", plan.getCurrency());
        body.put("interval", plan.getBillingInterval());
        body.put("status", plan.getStatus());
        body.put("modules", modules.findByPlanCode(plan.getCode()).stream().map(PlanModule::getModuleKey).toList());
        body.put("branchLimit", plan.getBranchLimit());
        body.put("userLimit", plan.getUserLimit());
        body.put("deviceLimit", plan.getDeviceLimit());
        body.put("productLimit", plan.getProductLimit());
        return body;
    }

    private Map<String, Object> paymentView(SubscriptionPayment payment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", payment.getId());
        body.put("businessId", payment.getBusinessId());
        body.put("plan", payment.getPlanCode());
        body.put("amount", payment.getAmount());
        body.put("currency", payment.getCurrency());
        body.put("status", payment.getStatus());
        body.put("provider", payment.getProvider());
        body.put("reference", payment.getProviderRef());
        body.put("createdAt", payment.getCreatedAt());
        body.put("verifiedAt", payment.getVerifiedAt());
        body.put("failureReason", payment.getFailureReason());
        return body;
    }

    private void applyRefund(Long businessId, String reference) {
        payments.findByProviderAndProviderRef("paystack", reference).ifPresent(payment -> {
            if (businessId != null && payment.getBusinessId() != null && !payment.getBusinessId().equals(businessId)) {
                throw new ApiException(409, "Refund reference belongs to a different business");
            }
            payment.setStatus("REFUNDED");
            subscriptions.findByProviderAndProviderRef("paystack", reference).ifPresent(account -> {
                if ("ACTIVE".equals(account.getStatus()) || "PAST_DUE".equals(account.getStatus())) {
                    account.setStatus("CANCELLED");
                    account.setCancelledAt(Instant.now());
                }
            });
            audit.log(payment.getBusinessId(), "Billing", "PAYMENT_REFUNDED", reference, "PAYMENT", reference, "");
        });
    }

    private void recordEvent(String eventId, String type, Long businessId, String reference, String summary, boolean processed) {
        if (eventId == null || eventId.isBlank() || billingEvents.findByProviderAndEventId("paystack", eventId).isPresent()) {
            return;
        }
        BillingEvent event = new BillingEvent();
        event.setProvider("paystack");
        event.setEventId(eventId);
        event.setEventType(type);
        event.setBusinessId(businessId);
        event.setProviderRef(reference == null ? "" : reference);
        event.setSummary(summary == null ? "" : clip(summary));
        event.setSignatureValid(true);
        event.setProcessed(processed);
        try {
            billingEvents.saveAndFlush(event);
        } catch (DataIntegrityViolationException ignored) {
            // A repeated Paystack delivery must not create a second trail row.
        }
    }

    private static Integer nonNegative(Integer value, String label) {
        if (value != null && value < 0) {
            throw new ApiException(400, label + " cannot be negative");
        }
        return value;
    }

    private static String clip(String value) {
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private Map<String, Object> eventView(BillingEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", event.getId());
        body.put("provider", event.getProvider());
        body.put("eventId", event.getEventId());
        body.put("eventType", event.getEventType());
        body.put("businessId", event.getBusinessId());
        body.put("reference", event.getProviderRef());
        body.put("summary", event.getSummary());
        body.put("signatureValid", event.isSignatureValid());
        body.put("processed", event.isProcessed());
        body.put("createdAt", event.getCreatedAt());
        return body;
    }
}
