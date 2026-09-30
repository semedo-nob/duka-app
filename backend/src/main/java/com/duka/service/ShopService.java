package com.duka.service;

import com.duka.domain.*;
import com.duka.integrations.etims.EtimsAdapter;
import com.duka.repo.*;
import com.duka.config.Phones;
import com.duka.security.Permission;
import com.duka.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.duka.web.ApiException;
import com.duka.web.Dto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopService {
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final List<String> CAPS = List.of("purchasing", "credit", "advReports", "multiBranch", "etims");

    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository purchaseOrders;
    private final BranchRepository branches;
    private final WarehouseRepository warehouses;
    private final ExpenseRepository expenses;
    private final TeamMemberRepository team;
    private final AuditEventRepository auditEvents;
    private final AppSettingRepository settings;
    private final SaleRepository sales;
    private final PaymentRepository payments;
    private final ProductRepository products;
    private final InventoryBalanceRepository balances;
    private final EtimsSubmissionRepository etimsSubmissions;
    private final EtimsService etims;
    private final EtimsAdapter etimsAdapter;
    private final AuditService audit;
    private final ObjectMapper json;
    private final SubscriptionService subscriptions;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final UserPermissionOverrideRepository permissionOverrides;
    private final ShiftRecordRepository shifts;
    private final DeviceRegistrationRepository devices;
    private final InstallationRepository installations;

    @Transactional(readOnly = true)
    public List<Dto.CustomerView> customers() {
        return customers.findByBusinessIdOrderByNameAsc(userBusiness()).stream().map(this::customerView).toList();
    }

    @Transactional
    public Dto.CustomerView createCustomer(Dto.CustomerRequest request, UserPrincipal user) {
        Customer customer = new Customer();
        customer.setBusinessId(user.getBusinessId());
        customer.setName(request.name().trim());
        customer.setPhone(request.phone() == null ? "" : request.phone());
        Customer saved = customers.save(customer);
        audit.log(user.getBusinessId(), user.getName(), "added new customer " + saved.getName(), "", "CUSTOMER", saved.getId().toString(), "");
        return customerView(saved);
    }

    @Transactional(readOnly = true)
    public List<Dto.SaleView> customerSales(long id, SaleService saleService) {
        if (customers.findByIdAndBusinessId(id, userBusiness()).isEmpty()) {
            throw new ApiException(404, "Customer not found");
        }
        return sales.findByBusinessIdAndCustomerIdOrderByCreatedAtDesc(String.valueOf(userBusiness()), id).stream().limit(20).map(sale -> saleService.get(sale.getId())).toList();
    }

    @Transactional
    public Dto.CustomerView recordCreditPayment(long id, BigDecimal amount, UserPrincipal user) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(400, "amount must be a positive number");
        }
        Customer customer = customers.findByIdAndBusinessId(id, user.getBusinessId()).orElseThrow(() -> new ApiException(404, "Customer not found"));
        BigDecimal next = customer.getBalance().subtract(amount);
        customer.setBalance(next.signum() < 0 ? BigDecimal.ZERO : next);
        audit.log(user.getBusinessId(), user.getName(), "recorded credit payment from " + customer.getName(), "KSh " + amount, "CUSTOMER", customer.getId().toString(), "");
        return customerView(customer);
    }

    @Transactional(readOnly = true)
    public List<Dto.BranchView> branches() {
        return branches.findByBusinessIdOrderByNameAsc(userBusiness()).stream()
                .map(branch -> new Dto.BranchView(branch.getId(), branch.getName(), branch.getSales(), branch.getStaff(), branch.getLowStock()))
                .toList();
    }

    @Transactional
    public Dto.BranchView createBranch(Dto.BranchRequest request, UserPrincipal user) {
        long existing = branches.findByBusinessIdOrderByNameAsc(user.getBusinessId()).size();
        subscriptions.assertCapacity(user.getBusinessId(), "branches", existing);
        if (existing >= 1 && !Boolean.TRUE.equals(subscriptions.entitlements(user.getBusinessId()).get("multiBranch"))) {
            throw new ApiException(403, "Another branch needs the multi-branch module. The first branch stays available.");
        }
        Branch branch = new Branch();
        branch.setBusinessId(user.getBusinessId());
        branch.setName(request.name().trim());
        branches.save(branch);
        audit.log(user.getBusinessId(), user.getName(), "added branch " + branch.getName(), "", "BRANCH", branch.getId().toString(), "");
        return new Dto.BranchView(branch.getId(), branch.getName(), branch.getSales(), branch.getStaff(), branch.getLowStock());
    }

    @Transactional(readOnly = true)
    public List<Dto.WarehouseView> warehouses() {
        return warehouses.findByBusinessIdOrderByNameAsc(userBusiness()).stream()
                .map(warehouse -> new Dto.WarehouseView(warehouse.getId(), warehouse.getName(), warehouse.getBranch(), warehouse.getStockValue()))
                .toList();
    }

    @Transactional
    public Dto.Ok transfer(Dto.TransferRequest request, UserPrincipal user) {
        audit.log(user.getBusinessId(), user.getName(),
                "recorded a warehouse transfer request from " + request.from() + " to " + request.to(),
                "Value noted: KSh " + Money.nz(request.amount()) + ". On-hand stock was not changed because quantities are tracked per product, not per warehouse.",
                "WAREHOUSE", "", "");
        return new Dto.Ok(true, "Transfer request recorded. Product stock was not moved.");
    }

    @Transactional(readOnly = true)
    public List<Dto.ExpenseView> expenses() {
        return expenses.findByBusinessIdOrderByCreatedAtDesc(userBusiness()).stream()
                .map(expense -> new Dto.ExpenseView(expense.getId(), expense.getAmount(), expense.getCategory(), expense.getMethod(), expense.getDescription(), expense.getCreatedAt()))
                .toList();
    }

    @Transactional
    public Dto.ExpenseView createExpense(Dto.ExpenseRequest request, UserPrincipal user) {
        if (request.amount().signum() < 0) {
            throw new ApiException(400, "amount cannot be negative");
        }
        Expense expense = new Expense();
        expense.setId(UUID.randomUUID().toString());
        expense.setAmount(Money.money(request.amount()));
        expense.setCategory(request.category() == null ? "Other" : request.category());
        expense.setMethod(request.method() == null ? "Cash" : request.method());
        expense.setBusinessId(user.getBusinessId());
        expense.setDescription(request.description() == null ? "" : request.description());
        expenses.save(expense);
        audit.log(user.getBusinessId(), user.getName(), "recorded " + expense.getCategory() + " expense", "KSh " + expense.getAmount(), "EXPENSE", expense.getId(), "");
        return new Dto.ExpenseView(expense.getId(), expense.getAmount(), expense.getCategory(), expense.getMethod(), expense.getDescription(), expense.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<Dto.TeamView> team() {
        return users.findByBusinessIdOrderByNameAsc(userBusiness()).stream()
                .map(this::teamView)
                .toList();
    }

    @Transactional
    public List<String> setPermission(long id, Dto.PermissionUpdate update, UserPrincipal user) {
        UserAccount member = users.findByIdAndBusinessId(id, user.getBusinessId()).orElseThrow(() -> new ApiException(404, "Staff member not found"));
        Permission permission;
        try {
            permission = Permission.valueOf(update.permission());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(400, "Unknown permission");
        }
        UserPermissionOverride.Key key = new UserPermissionOverride.Key();
        key.setUserId(member.getId());
        key.setPermission(permission.name());
        UserPermissionOverride override = permissionOverrides.findById(key).orElseGet(() -> {
            UserPermissionOverride created = new UserPermissionOverride();
            created.setId(key);
            return created;
        });
        override.setGranted(update.granted());
        permissionOverrides.save(override);
        audit.log(user.getBusinessId(), user.getName(), (update.granted() ? "granted " : "revoked ") + permission.name() + " for " + member.getName(), "", "USER", member.getId().toString(), "");
        return UserPrincipal.from(member, permissionOverrides.findByIdUserId(member.getId())).getPermissions().stream().map(Enum::name).sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<Dto.AuditView> auditLog() {
        return auditEvents.findTop100ByBusinessIdOrderByCreatedAtDesc(userBusiness()).stream()
                .limit(50)
                .map(event -> new Dto.AuditView(event.getId(), event.getActor(), event.getAction(), event.getDetail(), event.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> capabilities() {
        return subscriptions.entitlements(userBusiness());
    }

    public Map<String, Boolean> unlock(String key, UserPrincipal user) {
        throw new ApiException(403, "Paid modules unlock only after a verified subscription payment. Direct unlock is disabled.");
    }

    @Transactional(readOnly = true)
    public Dto.SettingsView settings() {
        return new Dto.SettingsView(section("business"), section("payments"), section("receipts"), section("tax"));
    }

    @Transactional
    public Map<String, String> updateSettings(String section, Map<String, String> patch, UserPrincipal user) {
        if (!List.of("business", "payments", "receipts", "tax", "device").contains(section)) {
            throw new ApiException(400, "Unknown settings section");
        }
        if ("payments".equals(section)) {
            com.duka.security.Access.require(Permission.MPESA_MANAGE);
        } else if ("tax".equals(section)) {
            com.duka.security.Access.require(Permission.ETIMS_MANAGE);
        } else if (!"receipts".equals(section)) {
            com.duka.security.Access.require(Permission.BUSINESS_MANAGE);
        } else if (!com.duka.security.Access.has(Permission.BUSINESS_MANAGE) && !com.duka.security.Access.has(Permission.REPORT_VIEW)) {
            throw new ApiException(403, "You don't have permission to do that");
        }
        Map<String, String> current = section(section);
        if (patch != null) {
            current.putAll(patch);
        }
        try {
            AppSetting setting = settings.findById(scoped(section)).orElseGet(() -> {
                AppSetting created = new AppSetting();
                created.setKey(section);
                return created;
            });
            setting.setKey(scoped(section));
            setting.setValue(json.writeValueAsString(current));
            settings.save(setting);
            audit.log(user.getBusinessId(), user.getName(), "updated " + section + " settings",
                    patch == null ? "" : String.join(", ", patch.keySet()), "SETTINGS", section, "");
            return current;
        } catch (Exception ex) {
            throw new ApiException(400, "Could not save settings");
        }
    }

    @Transactional(readOnly = true)
    public Dto.EtimsInfo etimsInfo() {
        Instant start = LocalDate.now(NAIROBI).atStartOfDay(NAIROBI).toInstant();
        List<EtimsSubmission> today = etimsSubmissions.findAll().stream()
                .filter(submission -> !submission.getCreatedAt().isBefore(start))
                .toList();
        int accepted = (int) today.stream().filter(row -> row.getStatus() == EtimsStatus.ACCEPTED).count();
        int failed = (int) today.stream().filter(row -> row.getStatus() == EtimsStatus.FAILED).count();
        int pending = (int) today.stream().filter(row -> row.getStatus() == EtimsStatus.PENDING).count();
        List<Dto.EtimsLog> logs = etimsSubmissions.findTop50ByOrderByCreatedAtDesc().stream()
                .map(row -> new Dto.EtimsLog("#" + row.getSaleId(), row.getStatus().label(), etims.timeFormat().format(row.getCreatedAt())))
                .toList();
        return new Dto.EtimsInfo(Boolean.TRUE.equals(subscriptions.entitlements(userBusiness()).get("etims")), etimsAdapter.configured(), etimsAdapter.mode(),
                "Invoices are stored locally. Nothing is sent to KRA until an official eTIMS contract is configured.",
                today.size(), accepted, failed, pending, logs);
    }

    @Transactional
    public Dto.DashboardView dashboard() {
        Instant start = LocalDate.now(NAIROBI).atStartOfDay(NAIROBI).toInstant();
        List<Sale> today = sales.findByBusinessIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(bizKey(), start);
        BigDecimal totalSales = BigDecimal.ZERO;
        int transactions = 0;
        int itemsSold = 0;
        BigDecimal profit = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal mpesa = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        for (Sale sale : today) {
            if (sale.getStatus() != SaleStatus.COMPLETED) {
                continue;
            }
            transactions++;
            BigDecimal kept = BigDecimal.ZERO;
            for (SaleItem item : sale.getItems()) {
                BigDecimal line = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                if (item.isRefunded()) {
                    refunded = refunded.add(line);
                    continue;
                }
                kept = kept.add(line);
                itemsSold += item.getQuantity();
                profit = profit.add(item.getUnitPrice().subtract(item.getUnitCost()).multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            if (!sale.isRefunded()) {
                totalSales = totalSales.add(sale.getTotal().subtract(refundedLines(sale)));
            }
            for (Payment payment : payments.findBySaleId(sale.getId())) {
                if (payment.getStatus() != PaymentStatus.COMPLETED) {
                    continue;
                }
                if (payment.getMethod() == PaymentMethod.CASH) {
                    cash = cash.add(payment.getAmount());
                } else if (payment.getMethod() == PaymentMethod.MPESA) {
                    mpesa = mpesa.add(payment.getAmount());
                } else if (payment.getMethod() == PaymentMethod.CARD) {
                    card = card.add(payment.getAmount());
                }
            }
        }
        int low = 0;
        List<String> lowNames = new ArrayList<>();
        for (Product product : products.findByBusinessId(userBusiness())) {
            int qty = balances.findById(product.getId()).map(InventoryBalance::getQuantity).orElse(0);
            if (qty <= product.getReorderLevel()) {
                low++;
                if (lowNames.size() < 3) {
                    lowNames.add(product.getName());
                }
            }
        }
        List<Dto.AlertView> alerts = new ArrayList<>();
        if (low > 0) {
            alerts.add(new Dto.AlertView("warn", low + (low == 1 ? " product is" : " products are") + " low on stock", String.join(", ", lowNames)));
        }
        if (!Boolean.TRUE.equals(subscriptions.entitlements(userBusiness()).get("etims"))) {
            alerts.add(new Dto.AlertView("neutral", "eTIMS isn't connected", "Connect it from the eTIMS page when you are ready to queue tax invoices"));
        }
        List<Integer> trend = new ArrayList<>();
        for (int daysAgo = 6; daysAgo >= 0; daysAgo--) {
            Instant from = start.minus(daysAgo, ChronoUnit.DAYS);
            Instant to = from.plus(1, ChronoUnit.DAYS);
            BigDecimal dayTotal = sales.findByBusinessIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(bizKey(), from).stream()
                    .filter(sale -> sale.getCreatedAt().isBefore(to) && sale.getStatus() == SaleStatus.COMPLETED && !sale.isRefunded())
                    .map(Sale::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            trend.add(dayTotal.intValue());
        }
        return new Dto.DashboardView(Money.money(totalSales), transactions, itemsSold, Money.money(profit),
                Money.money(cash), Money.money(mpesa), Money.money(card), low, Money.money(refunded), alerts, trend);
    }

    @Transactional(readOnly = true)
    public Dto.ReportsView reports(String period) {
        Instant from = switch (period == null ? "Today" : period) {
            case "This week" -> LocalDate.now(NAIROBI).minusDays(6).atStartOfDay(NAIROBI).toInstant();
            case "This month" -> LocalDate.now(NAIROBI).minusDays(29).atStartOfDay(NAIROBI).toInstant();
            case "Custom" -> Instant.now().minus(365, ChronoUnit.DAYS);
            default -> LocalDate.now(NAIROBI).atStartOfDay(NAIROBI).toInstant();
        };
        List<Sale> live = sales.findByBusinessIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(bizKey(), from).stream()
                .filter(sale -> sale.getStatus() == SaleStatus.COMPLETED)
                .toList();
        BigDecimal total = BigDecimal.ZERO;
        int itemsSold = 0;
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        Map<String, BigDecimal> byProduct = new LinkedHashMap<>();
        Map<String, BigDecimal> byCashier = new LinkedHashMap<>();
        int transactions = 0;
        for (Sale sale : live) {
            BigDecimal kept = BigDecimal.ZERO;
            boolean any = false;
            for (SaleItem item : sale.getItems()) {
                if (item.isRefunded()) {
                    continue;
                }
                any = true;
                BigDecimal line = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                kept = kept.add(line);
                itemsSold += item.getQuantity();
                byProduct.merge(item.getName(), line, BigDecimal::add);
            }
            if (!any) {
                continue;
            }
            transactions++;
            if (sale.getDiscount() != null && kept.signum() > 0) {
                kept = kept.subtract(sale.getDiscount());
                if (kept.signum() < 0) {
                    kept = BigDecimal.ZERO;
                }
            }
            total = total.add(kept);
            byMethod.merge(sale.getMethod(), kept, BigDecimal::add);
            String cashier = sale.getCreatedBy() == null ? "Unknown" : users.findById(sale.getCreatedBy()).map(UserAccount::getName).orElse("Staff");
            byCashier.merge(cashier, kept, BigDecimal::add);
        }
        BigDecimal avg = transactions == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(transactions), 2, RoundingMode.HALF_UP);
        List<Dto.NamedAmount> methods = byMethod.entrySet().stream().map(entry -> new Dto.NamedAmount(entry.getKey(), Money.money(entry.getValue()))).toList();
        List<Dto.NamedAmountName> top = byProduct.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(entry -> new Dto.NamedAmountName(entry.getKey(), Money.money(entry.getValue())))
                .toList();
        boolean advanced = Boolean.TRUE.equals(subscriptions.entitlements(userBusiness()).get("advReports"));
        List<Dto.NamedAmountName> cashiers = advanced ? byCashier.entrySet().stream()
                .map(entry -> new Dto.NamedAmountName(entry.getKey(), Money.money(entry.getValue())))
                .toList() : List.of();
        return new Dto.ReportsView(Money.money(total), transactions, itemsSold, avg, methods, top, cashiers);
    }

    private BigDecimal refundedLines(Sale sale) {
        return sale.getItems().stream()
                .filter(SaleItem::isRefunded)
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Dto.CustomerView customerView(Customer customer) {
        List<Sale> rows = sales.findByBusinessIdAndCustomerIdOrderByCreatedAtDesc(String.valueOf(customer.getBusinessId()), customer.getId()).stream()
                .filter(sale -> sale.getStatus() == SaleStatus.COMPLETED)
                .toList();
        BigDecimal total = rows.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Dto.CustomerView(customer.getId(), customer.getName(), customer.getPhone(), rows.size(), Money.money(total), customer.getBalance());
    }

    @Transactional
    public Map<String, Object> heartbeat(Dto.DeviceHeartbeat request, UserPrincipal user) {
        if (request.deviceId() == null || request.deviceId().isBlank()) {
            throw new ApiException(400, "deviceId is required");
        }
        DeviceRegistration device = devices.findById(request.deviceId()).orElse(null);
        boolean created = device == null;
        if (device == null) {
            subscriptions.assertCapacity(user.getBusinessId(), "devices", devices.findByBusinessId(user.getBusinessId()).size());
            device = new DeviceRegistration();
            device.setDeviceId(request.deviceId());
        }
        if (device.isRevoked()) {
            throw new ApiException(403, "This device has been revoked");
        }
        if (device.getBusinessId() != null && !device.getBusinessId().equals(user.getBusinessId())) {
            throw new ApiException(403, "This device is registered to a different business");
        }
        device.setBusinessId(user.getBusinessId());
        device.setInstallationId(request.installationId());
        device.setBranchId(request.branchId());
        if (request.name() != null) {
            device.setName(request.name());
        }
        if (request.appVersion() != null) {
            device.setAppVersion(request.appVersion());
        }
        if (request.pendingSync() != null) {
            device.setPendingSync(request.pendingSync());
        }
        if (request.failedSync() != null) {
            device.setFailedSync(request.failedSync());
        }
        if (request.lastError() != null) {
            device.setLastError(request.lastError());
        }
        if (request.printerStatus() != null) {
            device.setPrinterStatus(request.printerStatus());
        }
        if (request.scannerStatus() != null) {
            device.setScannerStatus(request.scannerStatus());
        }
        if (request.oldestPendingAt() != null && !request.oldestPendingAt().isBlank()) {
            device.setOldestPendingAt(Instant.parse(request.oldestPendingAt()));
        }
        device.setLastSyncAt(Instant.now());
        device.setLastSeen(Instant.now());
        devices.save(device);
        if (request.installationId() != null && !request.installationId().isBlank()) {
            Installation installation = installations.findById(request.installationId()).orElseGet(() -> {
                Installation row = new Installation();
                row.setId(request.installationId());
                return row;
            });
            installation.setBusinessId(user.getBusinessId());
            if (request.appVersion() != null) {
                installation.setAppVersion(request.appVersion());
            }
            installation.setLastSeenAt(Instant.now());
            installations.save(installation);
        }
        if (created) {
            audit.log(user.getBusinessId(), user.getName(), "DEVICE_REGISTERED", device.getDeviceId(), "DEVICE", device.getDeviceId(), device.getDeviceId());
        }
        return Map.of("deviceId", device.getDeviceId(), "revoked", false);
    }

    @Transactional
    public Map<String, Object> openShift(Dto.ShiftOpenRequest request, UserPrincipal user) {
        ShiftRecord shift = new ShiftRecord();
        shift.setBusinessId(user.getBusinessId());
        shift.setUserId(user.getId());
        shift.setDeviceId(request.deviceId());
        shift.setOpeningCash(Money.money(request.openingCash()));
        shifts.save(shift);
        audit.log(user.getBusinessId(), user.getName(), "opened a shift", "Opening cash " + shift.getOpeningCash(), "SHIFT", shift.getId().toString(), request.deviceId() == null ? "" : request.deviceId());
        return Map.of("id", shift.getId(), "openedAt", shift.getOpenedAt().toString(), "openingCash", shift.getOpeningCash());
    }

    @Transactional
    public Map<String, Object> closeShift(Dto.ShiftCloseRequest request, UserPrincipal user) {
        ShiftRecord shift = new ShiftRecord();
        shift.setBusinessId(user.getBusinessId());
        shift.setUserId(user.getId());
        shift.setDeviceId(request.deviceId());
        shift.setClosedAt(Instant.now());
        shift.setDeclaredCash(Money.money(request.declaredCash()));
        shift.setExpectedCash(Money.money(request.expectedCash()));
        if (request.expectedCash() != null) {
            shift.setVariance(Money.money(request.declaredCash().subtract(request.expectedCash())));
        }
        shifts.save(shift);
        audit.log(user.getBusinessId(), user.getName(), "closed a shift", "Declared " + shift.getDeclaredCash(), "SHIFT", shift.getId().toString(), request.deviceId() == null ? "" : request.deviceId());
        return Map.of("id", shift.getId(), "variance", shift.getVariance() == null ? BigDecimal.ZERO : shift.getVariance());
    }

    private Long userBusiness() {
        return com.duka.security.CurrentUser.require().getBusinessId();
    }

    private String bizKey() {
        return String.valueOf(userBusiness());
    }

    private Dto.TeamView teamView(UserAccount member) {
        List<String> permissions = UserPrincipal.from(member, permissionOverrides.findByIdUserId(member.getId()))
                .getPermissions().stream().map(Enum::name).sorted().toList();
        return new Dto.TeamView(member.getId().toString(), member.getName(), member.getPhone(), member.getRole().name(),
                member.getStatus() == null ? (member.isActive() ? "Active" : "Disabled") : switch (member.getStatus()) {
                    case ACTIVE -> "Active";
                    case INVITED -> "Invited";
                    case DISABLED -> "Disabled";
                    case LOCKED -> "Locked";
                    case DEACTIVATED -> "Deactivated";
                }, permissions, null);
    }

    private String scoped(String section) {
        return section + "." + userBusiness();
    }

    private Map<String, String> section(String key) {
        Map<String, String> scoped = readSection(scoped(key));
        if (!scoped.isEmpty()) {
            return scoped;
        }
        if (Long.valueOf(1L).equals(userBusiness())) {
            return readSection(key);
        }
        return new LinkedHashMap<>();
    }

    private Map<String, String> readSection(String key) {
        return settings.findById(key).map(setting -> {
            try {
                return json.readValue(setting.getValue(), new TypeReference<LinkedHashMap<String, String>>() {});
            } catch (Exception ex) {
                return new LinkedHashMap<String, String>();
            }
        }).orElseGet(LinkedHashMap::new);
    }
}
