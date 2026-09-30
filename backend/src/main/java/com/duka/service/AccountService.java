package com.duka.service;

import com.duka.config.Phones;
import com.duka.domain.*;
import com.duka.repo.*;
import com.duka.security.RolePermissions;
import com.duka.security.SecretTokens;
import com.duka.security.UserPrincipal;
import com.duka.web.ApiException;
import com.duka.web.Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {
    private static final String RECOVERY_MESSAGE =
            "Online recovery is currently unavailable. Contact Duka Support.";

    private final UserAccountRepository users;
    private final BusinessRepository businesses;
    private final UserSessionRepository sessions;
    private final StaffInvitationRepository invitations;
    private final RecoveryTokenRepository recoveryTokens;
    private final DeviceRegistrationRepository devices;
    private final UserPermissionOverrideRepository permissionOverrides;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final SaleRepository sales;
    private final ExpenseRepository expenses;
    private final PurchaseOrderRepository purchaseOrders;
    private final InventoryMovementRepository movements;
    private final AuditEventRepository auditEvents;
    private final PasswordEncoder encoder;
    private final SessionService sessionService;
    private final AuditService audit;
    private final SubscriptionService subscriptions;

    @Transactional(readOnly = true)
    public Map<String, Object> me(UserPrincipal principal) {
        UserAccount user = requireUser(principal.getId(), principal.getBusinessId());
        Business business = requireBusiness(principal.getBusinessId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId());
        body.put("name", user.getName());
        body.put("phone", user.getPhone());
        body.put("email", user.getEmail());
        body.put("employeeId", user.getEmployeeId());
        body.put("role", user.getRole().name());
        body.put("status", user.getStatus().name());
        body.put("branchId", user.getBranchId());
        body.put("lastLoginAt", user.getLastLoginAt());
        body.put("businessId", business.getId());
        body.put("businessName", business.getName());
        body.put("businessStatus", business.getStatus().name());
        body.put("passwordSet", user.getPasswordHash() != null && !user.getPasswordHash().isBlank());
        return body;
    }

    @Transactional
    public Map<String, Object> updateProfile(Dto.ProfileUpdate update, UserPrincipal principal) {
        UserAccount user = requireUser(principal.getId(), principal.getBusinessId());
        if (update.name() != null && !update.name().isBlank()) {
            user.setName(update.name().trim());
        }
        if (update.email() != null) {
            user.setEmail(update.email().trim());
        }
        audit.log(user.getBusinessId(), user.getName(), "BUSINESS_UPDATED", "Updated personal profile", "USER", user.getId().toString(), device());
        return me(principal);
    }

    @Transactional
    public Map<String, Object> setPassword(String currentPin, String password, UserPrincipal principal) {
        if (password == null || password.length() < 8) {
            throw new ApiException(400, "Account password must be at least 8 characters");
        }
        UserAccount user = requireUser(principal.getId(), principal.getBusinessId());
        if (user.getRole() != UserRole.OWNER && user.getRole() != UserRole.MANAGER) {
            throw new ApiException(403, "Only an owner or manager sets the account password. Cashiers keep using a PIN.");
        }
        if (!encoder.matches(currentPin, user.getPinHash())) {
            throw new ApiException(401, "Current PIN is incorrect");
        }
        user.setPasswordHash(encoder.encode(password));
        audit.log(user.getBusinessId(), user.getName(), "ACCOUNT_PASSWORD_SET", "Set an account password. The PIN was not stored.", "USER", user.getId().toString(), device());
        return Map.of("ok", true, "passwordSet", true);
    }

    public void requireAccountPassword(UserPrincipal principal, String password) {
        UserAccount user = requireUser(principal.getId(), principal.getBusinessId());
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ApiException(409, "Set an account password before changing billing, ownership, or closing the business. The till PIN stays for daily login.");
        }
        if (password == null || password.isBlank() || !encoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(401, "Account password is incorrect");
        }
    }

    @Transactional
    public Map<String, Object> changeCredential(Dto.CredentialChange request, UserPrincipal principal) {
        if (request.newPin().length() < 4) {
            throw new ApiException(400, "PIN must be at least 4 characters");
        }
        UserAccount user = requireUser(principal.getId(), principal.getBusinessId());
        if (!encoder.matches(request.currentPin(), user.getPinHash())) {
            throw new ApiException(401, "Current PIN is incorrect");
        }
        if (encoder.matches(request.newPin(), user.getPinHash())) {
            throw new ApiException(400, "Choose a different PIN");
        }
        user.setPinHash(encoder.encode(request.newPin()));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        sessionService.revokeOthers(user.getId(), currentSessionId());
        audit.log(user.getBusinessId(), user.getName(), "USER_PIN_CHANGED", "Changed their PIN", "USER", user.getId().toString(), device());
        return Map.of("ok", true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mySessions(UserPrincipal principal) {
        return sessions.findByUserIdOrderByCreatedAtDesc(principal.getId()).stream().map(this::sessionView).toList();
    }

    @Transactional
    public Map<String, Object> revokeMySession(String sessionId, UserPrincipal principal) {
        UserSession session = sessions.findByIdAndUserId(sessionId, principal.getId())
                .orElseThrow(() -> new ApiException(404, "Session not found"));
        sessionService.revoke(session);
        audit.log(principal.getBusinessId(), principal.getName(), "SESSION_REVOKED", "Revoked a session", "SESSION", sessionId, device());
        return sessionView(session);
    }

    @Transactional
    public Map<String, Object> logout(UserPrincipal principal) {
        String sessionId = currentSessionId();
        if (sessionId != null) {
            sessions.findByIdAndUserId(sessionId, principal.getId()).ifPresent(sessionService::revoke);
            audit.log(principal.getBusinessId(), principal.getName(), "SESSION_REVOKED", "Logged out", "SESSION", sessionId, device());
        }
        return Map.of("ok", true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> business(UserPrincipal principal) {
        Business business = requireBusiness(principal.getBusinessId());
        UserAccount owner = users.findByBusinessIdOrderByNameAsc(business.getId()).stream()
                .filter(user -> user.getRole() == UserRole.OWNER && user.getStatus() == AccountStatus.ACTIVE)
                .findFirst().orElse(null);
        Map<String, Object> body = profile(business);
        body.put("ownerName", owner == null ? "" : owner.getName());
        body.put("ownerPhone", owner == null ? "" : owner.getPhone());
        body.put("staffCount", users.findByBusinessIdOrderByNameAsc(business.getId()).size());
        return body;
    }

    @Transactional
    public Map<String, Object> updateBusiness(Dto.BusinessProfileUpdate update, UserPrincipal principal) {
        Business business = requireBusiness(principal.getBusinessId());
        if (update.name() != null && !update.name().isBlank()) business.setName(update.name().trim());
        if (update.legalName() != null) business.setLegalName(update.legalName().trim());
        if (update.phone() != null) business.setPhone(update.phone().trim());
        if (update.email() != null) business.setEmail(update.email().trim());
        if (update.address() != null) business.setAddress(update.address().trim());
        if (update.businessType() != null) business.setBusinessType(update.businessType().trim());
        if (update.category() != null) business.setCategory(update.category().trim());
        if (update.registrationNumber() != null) business.setRegistrationNumber(update.registrationNumber().trim());
        if (update.kraPin() != null) business.setKraPin(update.kraPin().trim());
        if (update.currency() != null && !update.currency().isBlank()) business.setCurrency(update.currency().trim());
        if (update.timezone() != null && !update.timezone().isBlank()) business.setTimezone(update.timezone().trim());
        if (update.receiptFooter() != null) business.setReceiptFooter(update.receiptFooter().trim());
        audit.log(business.getId(), principal.getName(), "BUSINESS_UPDATED", "Updated the business profile", "BUSINESS", business.getId().toString(), device());
        return profile(business);
    }

    @Transactional
    public Dto.TeamView invite(Dto.TeamRequest request, UserPrincipal principal) {
        if (request.pin() != null && !request.pin().isBlank()) {
            throw new ApiException(400, "Do not send a PIN. The staff member chooses their own PIN with the invitation code.");
        }
        if (request.phone() == null || request.phone().isBlank()) {
            throw new ApiException(400, "Phone is required so this person can log in");
        }
        UserRole role = parseRole(request.role());
        if (role == UserRole.OWNER) {
            throw new ApiException(400, "Use ownership transfer to add another owner");
        }
        subscriptions.assertCapacity(principal.getBusinessId(), "users", users.findByBusinessIdOrderByNameAsc(principal.getBusinessId()).size());
        String phone = Phones.normalize(request.phone());
        if (users.findByPhone(phone).isPresent()) {
            throw new ApiException(409, "That phone already has a login");
        }
        UserAccount member = new UserAccount();
        member.setBusinessId(principal.getBusinessId());
        member.setName(request.name().trim());
        member.setPhone(phone);
        member.setPinHash(encoder.encode(UUID.randomUUID().toString()));
        member.setRole(role);
        member.setStatus(AccountStatus.INVITED);
        member.setActive(false);
        users.save(member);
        String code = SecretTokens.code();
        StaffInvitation invitation = new StaffInvitation();
        invitation.setBusinessId(principal.getBusinessId());
        invitation.setUserId(member.getId());
        invitation.setTokenHash(SecretTokens.hash(code));
        invitation.setExpiresAt(Instant.now().plus(48, ChronoUnit.HOURS));
        invitation.setCreatedBy(principal.getId());
        invitations.save(invitation);
        audit.log(principal.getBusinessId(), principal.getName(), "USER_INVITED", member.getName() + " invited as " + role.name(), "USER", member.getId().toString(), device());
        return teamView(member, code);
    }

    @Transactional
    public Dto.AuthResponse acceptInvitation(Dto.InviteAccept request, String deviceId, String userAgent) {
        if (request.pin().length() < 4) {
            throw new ApiException(400, "PIN must be at least 4 characters");
        }
        StaffInvitation invitation = invitations.findByTokenHash(SecretTokens.hash(request.code()))
                .orElseThrow(() -> new ApiException(400, "Invitation code is invalid or already used"));
        if (invitation.getAcceptedAt() != null || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(400, "Invitation code is invalid or already used");
        }
        UserAccount user = users.findById(invitation.getUserId()).orElseThrow(() -> new ApiException(400, "Invitation code is invalid or already used"));
        if (user.getStatus() != AccountStatus.INVITED) {
            throw new ApiException(400, "Invitation code is invalid or already used");
        }
        user.setPinHash(encoder.encode(request.pin()));
        user.setStatus(AccountStatus.ACTIVE);
        user.setActive(true);
        invitation.setAcceptedAt(Instant.now());
        audit.log(user.getBusinessId(), user.getName(), "USER_ACTIVATED", "Accepted an invitation and chose a PIN", "USER", user.getId().toString(), deviceId == null ? "" : deviceId);
        return new Dto.AuthResponse(sessionService.open(user, deviceId, userAgent), userView(user));
    }

    @Transactional
    public Dto.TeamView setActive(long id, boolean active, UserPrincipal principal) {
        UserAccount member = requireUser(id, principal.getBusinessId());
        if (member.getId().equals(principal.getId())) {
            throw new ApiException(400, "You cannot disable your own login");
        }
        if (!active && member.getRole() == UserRole.OWNER
                && users.countByBusinessIdAndRoleAndStatus(principal.getBusinessId(), UserRole.OWNER, AccountStatus.ACTIVE) <= 1) {
            throw new ApiException(400, "Transfer ownership before disabling the only owner");
        }
        if (active) {
            if (member.getStatus() == AccountStatus.INVITED) {
                throw new ApiException(400, "This person still needs to accept their invitation");
            }
            member.setStatus(AccountStatus.ACTIVE);
            member.setActive(true);
            member.setFailedAttempts(0);
            member.setLockedUntil(null);
            audit.log(principal.getBusinessId(), principal.getName(), "USER_ACTIVATED", member.getName(), "USER", member.getId().toString(), device());
        } else {
            member.setStatus(AccountStatus.DISABLED);
            member.setActive(false);
            sessionService.revokeAll(member.getId());
            audit.log(principal.getBusinessId(), principal.getName(), "USER_DISABLED", member.getName(), "USER", member.getId().toString(), device());
        }
        return teamView(member, null);
    }

    @Transactional
    public Dto.TeamView setRole(long id, String roleName, UserPrincipal principal) {
        UserAccount member = requireUser(id, principal.getBusinessId());
        UserRole role = parseRole(roleName);
        if (role == UserRole.OWNER) {
            throw new ApiException(400, "Use ownership transfer to assign the owner role");
        }
        if (member.getRole() == UserRole.OWNER) {
            throw new ApiException(400, "Use ownership transfer to change the owner");
        }
        if (member.getId().equals(principal.getId())) {
            throw new ApiException(400, "You cannot change your own role");
        }
        UserRole previous = member.getRole();
        member.setRole(role);
        audit.log(principal.getBusinessId(), principal.getName(), "USER_ROLE_CHANGED", member.getName() + " from " + previous + " to " + role, "USER", member.getId().toString(), device());
        return teamView(member, null);
    }

    @Transactional
    public Map<String, Object> ownerTransfer(Dto.OwnerTransfer request, UserPrincipal principal) {
        if (!"TRANSFER".equals(request.confirm())) {
            throw new ApiException(400, "Type TRANSFER to confirm");
        }
        UserAccount owner = requireUser(principal.getId(), principal.getBusinessId());
        if (owner.getRole() != UserRole.OWNER) {
            throw new ApiException(403, "Only the owner can transfer the business");
        }
        requireAccountPassword(principal, request.password());
        if (!encoder.matches(request.pin(), owner.getPinHash())) {
            throw new ApiException(401, "PIN is incorrect");
        }
        UserAccount next = requireUser(request.userId(), principal.getBusinessId());
        if (next.getId().equals(owner.getId()) || next.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(400, "Choose an active staff member");
        }
        next.setRole(UserRole.OWNER);
        owner.setRole(UserRole.MANAGER);
        sessionService.revokeAll(next.getId());
        audit.log(owner.getBusinessId(), owner.getName(), "OWNER_TRANSFERRED", "Ownership moved to " + next.getName(), "USER", next.getId().toString(), device());
        return Map.of("ownerId", next.getId(), "previousOwnerRole", owner.getRole().name());
    }

    @Transactional
    public Map<String, String> issueRecoveryCode(long userId, UserPrincipal principal) {
        UserAccount member = requireUser(userId, principal.getBusinessId());
        if (member.getRole() == UserRole.OWNER) {
            throw new ApiException(400, "The owner recovers through Duka platform support");
        }
        if (member.getStatus() == AccountStatus.DEACTIVATED) {
            throw new ApiException(400, "This login is deactivated");
        }
        String code = storeRecovery(member);
        audit.log(principal.getBusinessId(), principal.getName(), "USER_INVITED", "Issued a recovery code for " + member.getName(), "USER", member.getId().toString(), device());
        return Map.of("code", code, "expiresInHours", "2");
    }

    public Map<String, String> requestRecovery(String phone) {
        if (Phones.normalize(phone).length() < 8) {
            throw new ApiException(400, "Enter the phone number on the account");
        }
        return Map.of("message", RECOVERY_MESSAGE, "delivery", "NOT CONFIGURED");
    }

    @Transactional
    public Dto.AuthResponse completeRecovery(Dto.RecoveryComplete request, String deviceId, String userAgent) {
        if (request.newPin().length() < 4) {
            throw new ApiException(400, "PIN must be at least 4 characters");
        }
        RecoveryToken token = recoveryTokens.findByTokenHash(SecretTokens.hash(request.code()))
                .orElseThrow(() -> new ApiException(400, "Recovery code is invalid or already used"));
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(400, "Recovery code is invalid or already used");
        }
        UserAccount user = users.findById(token.getUserId()).orElseThrow(() -> new ApiException(400, "Recovery code is invalid or already used"));
        if (user.getStatus() == AccountStatus.DEACTIVATED || user.getStatus() == AccountStatus.INVITED) {
            throw new ApiException(400, "Recovery code is invalid or already used");
        }
        user.setPinHash(encoder.encode(request.newPin()));
        user.setStatus(AccountStatus.ACTIVE);
        user.setActive(true);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        token.setUsedAt(Instant.now());
        sessionService.revokeAll(user.getId());
        audit.log(user.getBusinessId(), user.getName(), "USER_PIN_CHANGED", "Reset a PIN with a recovery code", "USER", user.getId().toString(), deviceId == null ? "" : deviceId);
        return new Dto.AuthResponse(sessionService.open(user, deviceId, userAgent), userView(user));
    }

    @Transactional
    public Map<String, String> platformOwnerRecovery(long businessId, String actor) {
        UserAccount owner = users.findByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(user -> user.getRole() == UserRole.OWNER)
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Owner not found"));
        String code = storeRecovery(owner);
        audit.log(businessId, actor, "USER_INVITED", "Platform issued an owner recovery code", "USER", owner.getId().toString(), "");
        return Map.of("code", code, "ownerName", owner.getName());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> businessSessions(UserPrincipal principal) {
        return sessions.findByBusinessIdOrderByCreatedAtDesc(principal.getBusinessId()).stream().limit(100).map(session -> {
            Map<String, Object> body = sessionView(session);
            users.findById(session.getUserId()).ifPresent(user -> body.put("userName", user.getName()));
            return body;
        }).toList();
    }

    @Transactional
    public Map<String, Object> revokeBusinessSession(String sessionId, UserPrincipal principal) {
        UserSession session = sessions.findByIdAndBusinessId(sessionId, principal.getBusinessId())
                .orElseThrow(() -> new ApiException(404, "Session not found"));
        sessionService.revoke(session);
        audit.log(principal.getBusinessId(), principal.getName(), "SESSION_REVOKED", "Revoked a staff session", "SESSION", sessionId, device());
        return sessionView(session);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> businessDevices(UserPrincipal principal) {
        return devices.findByBusinessId(principal.getBusinessId()).stream().map(this::deviceView).toList();
    }

    @Transactional
    public Map<String, Object> revokeDevice(String deviceId, UserPrincipal principal) {
        DeviceRegistration device = devices.findById(deviceId).orElseThrow(() -> new ApiException(404, "Device not found"));
        if (device.getBusinessId() == null || !device.getBusinessId().equals(principal.getBusinessId())) {
            throw new ApiException(404, "Device not found");
        }
        device.setRevoked(true);
        audit.log(principal.getBusinessId(), principal.getName(), "DEVICE_REVOKED", device.getName(), "DEVICE", deviceId, device());
        return deviceView(device);
    }

    @Transactional
    public Map<String, Object> closeBusiness(Dto.CloseBusiness request, UserPrincipal principal) {
        if (!"CLOSE".equals(request.confirm())) {
            throw new ApiException(400, "Type CLOSE to confirm");
        }
        UserAccount owner = requireUser(principal.getId(), principal.getBusinessId());
        if (owner.getRole() != UserRole.OWNER || !encoder.matches(request.pin(), owner.getPinHash())) {
            throw new ApiException(401, "PIN is incorrect");
        }
        requireAccountPassword(principal, request.password());
        Business business = requireBusiness(principal.getBusinessId());
        business.setStatus(BusinessStatus.CANCELLED);
        business.setCancelledAt(Instant.now());
        business.setRetentionUntil(Instant.now().plus(365 * 7, ChronoUnit.DAYS));
        audit.log(business.getId(), owner.getName(), "BUSINESS_SUSPENDED", "Business cancelled. Records stay until " + business.getRetentionUntil(), "BUSINESS", business.getId().toString(), device());
        return profile(business);
    }

    @Transactional(readOnly = true)
    public String export(String kind, UserPrincipal principal) {
        Long businessId = principal.getBusinessId();
        return switch (kind) {
            case "products" -> productsCsv(businessId);
            case "categories" -> categoriesCsv(businessId);
            case "customers" -> customersCsv(businessId);
            case "suppliers" -> suppliersCsv(businessId);
            case "sales" -> salesCsv(businessId);
            case "purchases" -> purchasesCsv(businessId);
            case "expenses" -> expensesCsv(businessId);
            case "movements" -> movementsCsv(businessId);
            case "audit" -> auditCsv(businessId);
            default -> throw new ApiException(400, "Unknown export");
        };
    }

    public void setBusinessActive(Business business, boolean active, String actor) {
        business.setActive(active);
        if (active) {
            business.setStatus(business.getStatus() == BusinessStatus.SUSPENDED || business.getStatus() == BusinessStatus.CANCELLED
                    ? BusinessStatus.REACTIVATED : BusinessStatus.ACTIVE);
            audit.log(business.getId(), actor, "BUSINESS_REACTIVATED", business.getName(), "BUSINESS", business.getId().toString(), "");
        } else {
            business.setStatus(BusinessStatus.SUSPENDED);
            audit.log(business.getId(), actor, "BUSINESS_SUSPENDED", business.getName(), "BUSINESS", business.getId().toString(), "");
        }
    }

    private String storeRecovery(UserAccount user) {
        String code = SecretTokens.code();
        RecoveryToken token = new RecoveryToken();
        token.setUserId(user.getId());
        token.setTokenHash(SecretTokens.hash(code));
        token.setExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
        recoveryTokens.save(token);
        return code;
    }

    private UserAccount requireUser(Long id, Long businessId) {
        return users.findByIdAndBusinessId(id, businessId).orElseThrow(() -> new ApiException(404, "Staff member not found"));
    }

    private Business requireBusiness(Long id) {
        return businesses.findById(id).orElseThrow(() -> new ApiException(404, "Business not found"));
    }

    private Map<String, Object> profile(Business business) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", business.getId());
        body.put("name", business.getName());
        body.put("legalName", business.getLegalName());
        body.put("phone", business.getPhone());
        body.put("email", business.getEmail());
        body.put("address", business.getAddress());
        body.put("businessType", business.getBusinessType());
        body.put("category", business.getCategory());
        body.put("registrationNumber", business.getRegistrationNumber());
        body.put("kraPin", business.getKraPin());
        body.put("currency", business.getCurrency());
        body.put("timezone", business.getTimezone());
        body.put("receiptFooter", business.getReceiptFooter());
        body.put("status", business.getStatus().name());
        body.put("active", business.isActive());
        body.put("cancelledAt", business.getCancelledAt());
        body.put("retentionUntil", business.getRetentionUntil());
        return body;
    }

    private Map<String, Object> sessionView(UserSession session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", session.getId());
        body.put("deviceId", session.getDeviceId());
        body.put("createdAt", session.getCreatedAt());
        body.put("lastSeenAt", session.getLastSeenAt());
        body.put("expiresAt", session.getExpiresAt());
        body.put("revoked", session.getRevokedAt() != null);
        body.put("current", session.getId().equals(currentSessionId()));
        return body;
    }

    private Map<String, Object> deviceView(DeviceRegistration device) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", device.getDeviceId());
        body.put("installationId", device.getInstallationId());
        body.put("branchId", device.getBranchId());
        body.put("name", device.getName());
        body.put("appVersion", device.getAppVersion());
        body.put("lastSeen", device.getLastSeen());
        body.put("revoked", device.isRevoked());
        return body;
    }

    private Dto.TeamView teamView(UserAccount member, String invitationCode) {
        List<String> permissions = UserPrincipal.from(member, permissionOverrides.findByIdUserId(member.getId()))
                .getPermissions().stream().map(Enum::name).sorted().toList();
        return new Dto.TeamView(member.getId().toString(), member.getName(), member.getPhone(), member.getRole().name(),
                label(member.getStatus()), permissions, invitationCode);
    }

    private Dto.UserView userView(UserAccount user) {
        var permissions = RolePermissions.defaults(user.getRole()).stream().map(Enum::name).sorted().toList();
        return new Dto.UserView(user.getId(), user.getName(), user.getPhone(), user.getRole().name(), user.getBusinessId(), permissions);
    }

    private static String label(AccountStatus status) {
        return switch (status) {
            case ACTIVE -> "Active";
            case INVITED -> "Invited";
            case DISABLED -> "Disabled";
            case LOCKED -> "Locked";
            case DEACTIVATED -> "Deactivated";
        };
    }

    private static UserRole parseRole(String role) {
        if (role == null) {
            return UserRole.CASHIER;
        }
        return switch (role.trim().toLowerCase()) {
            case "manager" -> UserRole.MANAGER;
            case "storekeeper" -> UserRole.STOREKEEPER;
            case "inventory" -> UserRole.INVENTORY;
            case "owner" -> UserRole.OWNER;
            default -> UserRole.CASHIER;
        };
    }

    private static String currentSessionId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            return null;
        }
        return auth.getDetails().toString();
    }

    private static String device() {
        return "";
    }

    private String productsCsv(Long businessId) {
        StringBuilder csv = new StringBuilder("name,sku,barcode,price,cost\n");
        for (Product product : products.findByBusinessId(businessId)) {
            csv.append(cell(product.getName())).append(',')
                    .append(cell(product.getSku())).append(',')
                    .append(cell(product.getBarcode())).append(',')
                    .append(product.getPrice()).append(',')
                    .append(product.getCost()).append('\n');
        }
        return csv.toString();
    }

    private String categoriesCsv(Long businessId) {
        StringBuilder csv = new StringBuilder("name,parentId\n");
        for (Category category : categories.findByBusinessIdOrderByNameAsc(businessId)) {
            csv.append(cell(category.getName())).append(',').append(category.getParentId() == null ? "" : category.getParentId()).append('\n');
        }
        return csv.toString();
    }

    private String customersCsv(Long businessId) {
        StringBuilder csv = new StringBuilder("name,phone,balance\n");
        for (Customer customer : customers.findByBusinessIdOrderByNameAsc(businessId)) {
            csv.append(cell(customer.getName())).append(',').append(cell(customer.getPhone())).append(',').append(customer.getBalance()).append('\n');
        }
        return csv.toString();
    }

    private String suppliersCsv(Long businessId) {
        StringBuilder csv = new StringBuilder("name,phone,email,address\n");
        for (Supplier supplier : suppliers.findByBusinessIdOrderByNameAsc(businessId)) {
            csv.append(cell(supplier.getName())).append(',').append(cell(supplier.getPhone())).append(',')
                    .append(cell(supplier.getEmail())).append(',').append(cell(supplier.getAddress())).append('\n');
        }
        return csv.toString();
    }

    private String salesCsv(Long businessId) {
        StringBuilder csv = new StringBuilder("id,status,method,total,createdAt\n");
        for (Sale sale : sales.findByBusinessIdOrderByCreatedAtDesc(String.valueOf(businessId))) {
            csv.append(sale.getId()).append(',').append(sale.getStatus()).append(',').append(cell(sale.getMethod())).append(',')
                    .append(sale.getTotal()).append(',').append(sale.getCreatedAt()).append('\n');
        }
        return csv.toString();
    }

    private String purchasesCsv(Long businessId) {
        StringBuilder csv = new StringBuilder("id,supplier,items,total,status,createdAt\n");
        for (PurchaseOrder order : purchaseOrders.findByBusinessIdOrderByCreatedAtDesc(businessId)) {
            csv.append(cell(order.getId())).append(',').append(cell(order.getSupplier())).append(',').append(order.getItems()).append(',')
                    .append(order.getTotal()).append(',').append(cell(order.getStatus())).append(',').append(order.getCreatedAt()).append('\n');
        }
        return csv.toString();
    }

    private String expensesCsv(Long businessId) {
        StringBuilder csv = new StringBuilder("id,amount,category,method,description,createdAt\n");
        for (Expense expense : expenses.findByBusinessIdOrderByCreatedAtDesc(businessId)) {
            csv.append(cell(expense.getId())).append(',').append(expense.getAmount()).append(',').append(cell(expense.getCategory())).append(',')
                    .append(cell(expense.getMethod())).append(',').append(cell(expense.getDescription())).append(',').append(expense.getCreatedAt()).append('\n');
        }
        return csv.toString();
    }

    private String movementsCsv(Long businessId) {
        StringBuilder csv = new StringBuilder("id,productId,type,quantity,note,createdAt\n");
        List<Long> ids = products.findByBusinessId(businessId).stream().map(Product::getId).toList();
        if (ids.isEmpty()) {
            return csv.toString();
        }
        for (InventoryMovement movement : movements.findByProductIdInOrderByCreatedAtDesc(ids)) {
            csv.append(movement.getId()).append(',').append(movement.getProductId()).append(',').append(movement.getMovementType()).append(',')
                    .append(movement.getQuantity()).append(',').append(cell(movement.getNote())).append(',').append(movement.getCreatedAt()).append('\n');
        }
        return csv.toString();
    }

    private String auditCsv(Long businessId) {
        StringBuilder csv = new StringBuilder("id,actor,action,detail,createdAt\n");
        for (AuditEvent event : auditEvents.findTop100ByBusinessIdOrderByCreatedAtDesc(businessId)) {
            csv.append(cell(event.getId())).append(',').append(cell(event.getActor())).append(',').append(cell(event.getAction())).append(',')
                    .append(cell(event.getDetail())).append(',').append(event.getCreatedAt()).append('\n');
        }
        return csv.toString();
    }

    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace("\"", "\"\"");
        if (safe.startsWith("=") || safe.startsWith("+") || safe.startsWith("-") || safe.startsWith("@")) {
            safe = "'" + safe;
        }
        return "\"" + safe + "\"";
    }
}
