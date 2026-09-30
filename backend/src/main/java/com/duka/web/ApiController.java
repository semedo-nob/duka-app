package com.duka.web;

import com.duka.config.DukaProperties;
import com.duka.integrations.mpesa.MpesaGateway;
import com.duka.security.CurrentUser;
import com.duka.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final AuthService auth;
    private final CatalogService catalog;
    private final SaleService sales;
    private final ShopService shop;
    private final StockReceiptService receipts;
    private final ExtractionService extractions;
    private final EtimsService etims;
    private final MpesaGateway mpesa;
    private final DukaProperties properties;
    private final SupplierService supplierService;
    private final SubscriptionService subscriptions;
    private final CategoryTemplateService categoryTemplates;
    private final SupportService support;
    private final AccountService accounts;

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/auth/login")
    Dto.AuthResponse login(@Valid @RequestBody Dto.LoginRequest request, jakarta.servlet.http.HttpServletRequest http) {
        return auth.login(request, http.getHeader("X-Duka-Device-Id"), http.getHeader("User-Agent"));
    }

    @PostMapping("/auth/register")
    Dto.AuthResponse register(@Valid @RequestBody Dto.RegisterRequest request, jakarta.servlet.http.HttpServletRequest http) {
        return auth.register(request, http.getHeader("X-Duka-Device-Id"), http.getHeader("User-Agent"));
    }

    @GetMapping("/products")
    java.util.List<Dto.ProductView> products() {
        return catalog.list();
    }

    @GetMapping("/products/barcode/{barcode}")
    Dto.ProductView barcode(@PathVariable String barcode) {
        return catalog.byBarcode(barcode);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    Dto.ProductView updateProduct(@PathVariable long id, @RequestBody Dto.UpdateProductRequest request) {
        return catalog.update(id, request);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    Dto.ProductView createProduct(@Valid @RequestBody Dto.CreateProductRequest request) {
        return catalog.create(request);
    }

    @PostMapping("/products/{id}/receive")
    @PreAuthorize("hasAuthority('INVENTORY_RECEIVE')")
    Dto.ProductView receive(@PathVariable long id, @Valid @RequestBody Dto.ReceiveRequest request) {
        return catalog.receive(id, request, CurrentUser.require());
    }

    @PostMapping("/products/{id}/adjust")
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    Dto.ProductView adjust(@PathVariable long id, @Valid @RequestBody Dto.AdjustRequest request) {
        return catalog.adjust(id, request, CurrentUser.require());
    }

    @GetMapping("/products/{id}/movements")
    java.util.List<Dto.MovementView> movements(@PathVariable long id) {
        return catalog.movements(id);
    }

    @GetMapping("/categories")
    java.util.List<Dto.CategoryView> categories() {
        return catalog.categories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    Dto.CategoryView createCategory(@Valid @RequestBody Dto.CategoryRequest request) {
        return catalog.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    Dto.CategoryView renameCategory(@PathVariable long id, @Valid @RequestBody Dto.CategoryRequest request) {
        return catalog.renameCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    void deleteCategory(@PathVariable long id) {
        catalog.deleteCategory(id);
    }

    @GetMapping("/category-templates")
    com.fasterxml.jackson.databind.JsonNode categoryTemplates() {
        return categoryTemplates.templateCatalog();
    }

    @PostMapping("/category-templates/{key}/apply")
    @PreAuthorize("hasAuthority('PRODUCT_EDIT')")
    java.util.List<Dto.CategoryView> applyTemplate(@PathVariable String key) {
        return categoryTemplates.apply(key, CurrentUser.require());
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    java.util.List<Dto.SaleView> sales() {
        return sales.recent();
    }

    @GetMapping("/sales/{id}")
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    Dto.SaleView sale(@PathVariable long id) {
        return sales.get(id);
    }

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    Dto.SaleView createSale(@Valid @RequestBody Dto.CreateSaleRequest request,
                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                            @RequestHeader(value = "X-Duka-Device-Id", required = false) String deviceId,
                            @RequestHeader(value = "X-Duka-Installation-Id", required = false) String installationId,
                            @RequestHeader(value = "X-Duka-Client-Sale-No", required = false) String clientSaleNo,
                            @RequestHeader(value = "X-Duka-Business-Id", required = false) String businessId) {
        return sales.create(request, idempotencyKey, CurrentUser.require(), deviceId, installationId, clientSaleNo, businessId);
    }

    @PostMapping("/sales/{id}/refund")
    @PreAuthorize("hasAuthority('SALE_RETURN')")
    Dto.SaleView refund(@PathVariable long id, @RequestBody Dto.RefundRequest request) {
        return sales.refund(id, request, CurrentUser.require());
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    java.util.List<Dto.CustomerView> customers() {
        return shop.customers();
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    Dto.CustomerView createCustomer(@Valid @RequestBody Dto.CustomerRequest request) {
        return shop.createCustomer(request, CurrentUser.require());
    }

    @GetMapping("/customers/{id}/sales")
    java.util.List<Dto.SaleView> customerSales(@PathVariable long id) {
        return shop.customerSales(id, sales);
    }

    @PostMapping("/customers/{id}/payments")
    Dto.CustomerView creditPayment(@PathVariable long id, @Valid @RequestBody Dto.AmountRequest request) {
        return shop.recordCreditPayment(id, request.amount(), CurrentUser.require());
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('SUPPLIER_VIEW')")
    java.util.List<Dto.SupplierView> suppliers(@RequestParam(required = false) String q) {
        return supplierService.list(CurrentUser.require(), q);
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_VIEW')")
    Dto.SupplierDetail supplier(@PathVariable long id) {
        return supplierService.get(id, CurrentUser.require());
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SUPPLIER_CREATE')")
    Dto.SupplierView createSupplier(@Valid @RequestBody Dto.SupplierRequest request) {
        return supplierService.create(request, CurrentUser.require());
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_EDIT')")
    Dto.SupplierView updateSupplier(@PathVariable long id, @Valid @RequestBody Dto.SupplierRequest request) {
        return supplierService.update(id, request, CurrentUser.require());
    }

    @PostMapping("/suppliers/{id}/offers")
    @PreAuthorize("hasAuthority('SUPPLIER_EDIT')")
    Dto.SupplierOfferView supplierOffer(@PathVariable long id, @Valid @RequestBody Dto.SupplierOfferRequest request) {
        return supplierService.saveOffer(id, request, CurrentUser.require());
    }

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    java.util.List<Dto.PurchaseOrderView> purchaseOrders() {
        return supplierService.orders(CurrentUser.require());
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    Dto.PurchaseOrderView createPurchaseOrder(@RequestBody Dto.PurchaseOrderRequest request) {
        return supplierService.createOrder(request, CurrentUser.require());
    }

    @PostMapping("/purchase-orders/{id}/receive")
    @PreAuthorize("hasAuthority('PURCHASE_APPROVE')")
    Dto.PurchaseOrderView receivePurchaseOrder(@PathVariable String id) {
        return supplierService.receive(id, CurrentUser.require());
    }

    @GetMapping("/branches")
    java.util.List<Dto.BranchView> branches() {
        return shop.branches();
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Dto.BranchView createBranch(@Valid @RequestBody Dto.BranchRequest request) {
        return shop.createBranch(request, CurrentUser.require());
    }

    @GetMapping("/warehouses")
    java.util.List<Dto.WarehouseView> warehouses() {
        return shop.warehouses();
    }

    @PostMapping("/warehouses/transfer")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Dto.Ok transfer(@RequestBody Dto.TransferRequest request) {
        return shop.transfer(request, CurrentUser.require());
    }

    @GetMapping("/expenses")
    @PreAuthorize("hasAuthority('EXPENSE_VIEW')")
    java.util.List<Dto.ExpenseView> expenses() {
        return shop.expenses();
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    Dto.ExpenseView createExpense(@Valid @RequestBody Dto.ExpenseRequest request) {
        return shop.createExpense(request, CurrentUser.require());
    }

    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    java.util.List<Dto.TeamView> team() {
        return shop.team();
    }

    @PostMapping("/team")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_CREATE')")
    Dto.TeamView addTeam(@Valid @RequestBody Dto.TeamRequest request) {
        return accounts.invite(request, CurrentUser.require());
    }

    @PostMapping("/team/{id}/active")
    @PreAuthorize("hasAuthority('USER_DISABLE')")
    Dto.TeamView setTeamActive(@PathVariable long id, @RequestParam boolean active) {
        return accounts.setActive(id, active, CurrentUser.require());
    }

    @PutMapping("/team/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    java.util.List<String> setPermission(@PathVariable long id, @Valid @RequestBody Dto.PermissionUpdate update) {
        return shop.setPermission(id, update, CurrentUser.require());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    java.util.List<Dto.AuditView> audit() {
        return shop.auditLog();
    }

    @GetMapping("/capabilities")
    Map<String, Boolean> capabilities() {
        return shop.capabilities();
    }

    @PostMapping("/capabilities/{key}/unlock")
    @PreAuthorize("hasRole('OWNER')")
    Map<String, Boolean> unlock(@PathVariable String key) {
        return shop.unlock(key, CurrentUser.require());
    }

    @GetMapping("/settings")
    Dto.SettingsView settings() {
        return shop.settings();
    }

    @PutMapping("/settings/{section}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Map<String, String> updateSettings(@PathVariable String section, @RequestBody Map<String, String> patch) {
        return shop.updateSettings(section, patch, CurrentUser.require());
    }

    @GetMapping("/dashboard")
    Dto.DashboardView dashboard() {
        return shop.dashboard();
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('REPORT_FINANCIAL')")
    Dto.ReportsView reports(@RequestParam(required = false) String period) {
        return shop.reports(period);
    }

    @GetMapping("/etims")
    Dto.EtimsInfo etimsInfo() {
        return shop.etimsInfo();
    }

    @PostMapping("/etims/retry")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Dto.Ok retryEtims() {
        return new Dto.Ok(true, etims.retryAll());
    }

    @GetMapping("/inventory/receipts")
    java.util.List<Dto.StockReceiptView> receiptList() {
        return receipts.list();
    }

    @PostMapping("/inventory/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    Dto.StockReceiptView createReceipt(@Valid @RequestBody Dto.CreateReceiptRequest request) {
        return receipts.create(request, CurrentUser.require());
    }

    @PostMapping("/inventory/receipts/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Dto.StockReceiptView approveReceipt(@PathVariable long id) {
        return receipts.approve(id, CurrentUser.require());
    }

    @PostMapping("/inventory/receipts/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Dto.StockReceiptView rejectReceipt(@PathVariable long id) {
        return receipts.reject(id, CurrentUser.require());
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Dto.ExtractionView upload(@RequestParam("file") MultipartFile file,
                              @RequestParam(required = false) String supplierName,
                              @RequestParam(required = false) String invoiceNumber) {
        return extractions.ingest(file, supplierName, invoiceNumber, CurrentUser.require());
    }

    @GetMapping("/receipt-reviews")
    java.util.List<Dto.ExtractionView> reviews(@RequestParam(required = false) String status) {
        return extractions.list(status);
    }

    @GetMapping("/receipt-reviews/{id}")
    Dto.ExtractionView review(@PathVariable long id) {
        return extractions.get(id);
    }

    @PutMapping("/receipt-reviews/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Dto.ExtractionView updateReview(@PathVariable long id, @RequestBody Dto.ExtractionUpdate update) {
        return extractions.update(id, update);
    }

    @PostMapping("/receipt-reviews/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Dto.ExtractionView approveReview(@PathVariable long id) {
        return extractions.approve(id, CurrentUser.require());
    }

    @PostMapping("/receipt-reviews/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    Dto.ExtractionView rejectReview(@PathVariable long id) {
        return extractions.reject(id, CurrentUser.require());
    }

    @PostMapping("/integrations/mpesa/callback")
    Dto.SaleView mpesaCallback(@RequestHeader(value = "X-Duka-Webhook-Secret", required = false) String secret,
                               @Valid @RequestBody Dto.MpesaCallback body) {
        String expected = properties.getMpesa().getWebhookSecret();
        if (expected == null || expected.isBlank() || !expected.equals(secret)) {
            throw new ApiException(401, "Webhook secret is missing or incorrect");
        }
        boolean success = "SUCCESS".equalsIgnoreCase(body.status());
        if (!success && !"FAILED".equalsIgnoreCase(body.status())) {
            throw new ApiException(400, "status must be SUCCESS or FAILED");
        }
        return sales.confirmProvider(body.externalRef(), success, body.providerReceipt());
    }

    @PostMapping("/integrations/mpesa/sandbox/confirm")
    Dto.SaleView sandboxConfirm(@Valid @RequestBody Dto.RefRequest body) {
        if (!mpesa.mockMode()) {
            throw new ApiException(404, "Sandbox confirmation is only available when MPESA_MODE=mock");
        }
        return sales.confirmProvider(body.externalRef(), true, "SANDBOX-" + body.externalRef());
    }

    @GetMapping("/integrations/mpesa")
    Map<String, Object> mpesaStatus() {
        return Map.of(
                "mode", mpesa.mockMode() ? "mock" : "live",
                "message", mpesa.mockMode()
                        ? "Sandbox mode. Payments stay pending until the sandbox confirmation or a verified callback."
                        : "Live mode will not report success until a real Daraja adapter is connected."
        );
    }

    @GetMapping("/integrations/etims")
    Dto.EtimsInfo etimsBoundary() {
        return shop.etimsInfo();
    }

    @GetMapping("/subscription/plans")
    java.util.List<Map<String, Object>> subscriptionPlans() {
        return subscriptions.publicPlans();
    }

    @GetMapping("/subscription")
    Map<String, Object> subscription() {
        return subscriptions.status(CurrentUser.require().getBusinessId());
    }

    @PostMapping("/subscription/checkout")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_MANAGE')")
    Map<String, Object> checkout(@RequestBody Map<String, String> body) {
        var user = CurrentUser.require();
        accounts.requireAccountPassword(user, body.get("accountPassword"));
        return subscriptions.checkout(body.get("plan"), body.get("email"), body.get("channel"), body.get("phone"), user);
    }

    @PostMapping("/subscription/verify")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_MANAGE')")
    Map<String, Object> verifySubscription(@RequestBody Map<String, String> body) {
        return subscriptions.verify(body.get("reference"), CurrentUser.require());
    }

    @PostMapping("/subscription/cancel")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_MANAGE')")
    Map<String, Object> cancelSubscription(@RequestBody(required = false) Map<String, String> body) {
        var user = CurrentUser.require();
        accounts.requireAccountPassword(user, body == null ? null : body.get("accountPassword"));
        return subscriptions.cancel(user);
    }

    @PostMapping("/subscription/restore")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_MANAGE')")
    Map<String, Object> restoreSubscription(@RequestBody(required = false) Map<String, String> body) {
        var user = CurrentUser.require();
        accounts.requireAccountPassword(user, body == null ? null : body.get("accountPassword"));
        return subscriptions.restore(user);
    }

    @PostMapping("/integrations/billing/webhook")
    Map<String, Object> billingWebhook(@RequestHeader(value = "x-paystack-signature", required = false) String signature,
                                       @RequestBody String rawBody) {
        return subscriptions.handleWebhook(rawBody, signature);
    }

    @GetMapping("/support")
    java.util.List<Map<String, Object>> supportCases() {
        return support.list(CurrentUser.require());
    }

    @PostMapping("/support")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> openSupport(@Valid @RequestBody Dto.SupportRequest request) {
        return support.open(request, CurrentUser.require());
    }

    @GetMapping("/support/{id}")
    Map<String, Object> supportCase(@PathVariable long id) {
        return support.get(id, CurrentUser.require());
    }

    @PostMapping("/support/{id}/reply")
    Map<String, Object> supportReply(@PathVariable long id, @Valid @RequestBody Dto.SupportReply reply) {
        return support.customerReply(id, reply.message(), CurrentUser.require());
    }

    @PostMapping("/devices/heartbeat")
    Map<String, Object> heartbeat(@RequestBody Dto.DeviceHeartbeat request) {
        return shop.heartbeat(request, CurrentUser.require());
    }

    @PostMapping("/shifts/open")
    Map<String, Object> openShift(@RequestBody Dto.ShiftOpenRequest request) {
        return shop.openShift(request, CurrentUser.require());
    }

    @PostMapping("/shifts/close")
    Map<String, Object> closeShift(@Valid @RequestBody Dto.ShiftCloseRequest request) {
        return shop.closeShift(request, CurrentUser.require());
    }
}
