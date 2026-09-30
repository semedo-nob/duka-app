package com.duka.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Dto {
    private Dto() {}

    public record ProductView(long id, String name, String sku, String barcode, BigDecimal price, BigDecimal cost,
                              int stock, int reorder, String cat, String emoji, String color, String unit,
                              BigDecimal taxRate, boolean active, String brand, String taxCategory, Long supplierId,
                              Long categoryId, String parentCategory) {}

    public record CreateProductRequest(@NotBlank String name, @NotNull BigDecimal price, BigDecimal cost, String sku,
                                       String cat, Integer reorder, String barcode, String unit, BigDecimal taxRate,
                                       String brand, String taxCategory, Long supplierId, Long categoryId) {}

    public record UpdateProductRequest(String name, BigDecimal price, BigDecimal cost, String sku, String barcode,
                                       String cat, Integer reorder, String unit, BigDecimal taxRate, String brand,
                                       String taxCategory, Long supplierId, Long categoryId, Boolean active) {}

    public record ReceiveRequest(@NotNull Integer qty, BigDecimal cost, String supplier) {}

    public record AdjustRequest(@NotNull Integer delta, String reason) {}

    public record MovementView(long id, long productId, String type, int qty, String note, Instant at) {}

    public record CategoryView(long id, String name, Long parentId) {}

    public record CategoryRequest(@NotBlank String name, Long parentId) {}

    public record ApplyTemplateRequest(@NotBlank String key) {}

    public record SaleItemRequest(@NotNull Long productId, String name, @NotNull Integer qty, @NotNull BigDecimal price) {}

    public record PaymentPart(@NotBlank String method, @NotNull BigDecimal amount) {}

    public record CreateSaleRequest(@NotEmpty List<@Valid SaleItemRequest> items, @NotBlank String method,
                                    Long customerId, BigDecimal discount, List<@Valid PaymentPart> payments,
                                    BigDecimal tendered) {}

    public record SaleItemView(long productId, String name, int qty, BigDecimal price, boolean refunded) {}

    public record SaleView(long id, List<SaleItemView> items, BigDecimal total, String method, Instant at,
                           Long customerId, String customerName, String etimsStatus, boolean refunded,
                           String status, String paymentStatus, Long paymentId, String paymentRef,
                           BigDecimal discount, BigDecimal tax, BigDecimal subtotal) {}

    public record RefundRequest(List<Integer> itemIndices, String reason) {}

    public record CustomerView(long id, String name, String phone, long purchases, BigDecimal total, BigDecimal balance) {}

    public record CustomerRequest(@NotBlank String name, String phone) {}

    public record AmountRequest(@NotNull BigDecimal amount) {}

    public record SupplierView(long id, String name, String contact, String products, String phone, String email,
                               String address, String category, String notes, boolean active, BigDecimal balance) {}

    public record SupplierRequest(@NotBlank String name, String contact, String products, String phone, String email,
                                  String address, String category, String notes, BigDecimal balance, Boolean active) {}

    public record SupplierOfferView(long id, long productId, String productName, String supplierSku, String alias, BigDecimal unitCost) {}

    public record SupplierOfferRequest(@NotNull Long productId, String supplierSku, String alias, BigDecimal unitCost) {}

    public record SupplierHistory(long id, String kind, String reference, BigDecimal total, String status, Instant at) {}

    public record SupplierDetail(SupplierView supplier, java.util.List<SupplierOfferView> offers, java.util.List<SupplierHistory> history) {}

    public record PurchaseOrderView(String id, String supplier, int items, BigDecimal total, String status, Long supplierId, boolean stockApplied) {}

    public record PurchaseOrderLineRequest(Long productId, Integer qty, BigDecimal unitCost) {}

    public record PurchaseOrderRequest(String supplier, Long supplierId, Integer items, BigDecimal total, java.util.List<PurchaseOrderLineRequest> lines) {}

    public record BranchView(long id, String name, BigDecimal sales, int staff, int lowStock) {}

    public record BranchRequest(@NotBlank String name) {}

    public record WarehouseView(long id, String name, String branch, BigDecimal stockValue) {}

    public record TransferRequest(String from, String to, BigDecimal amount) {}

    public record ExpenseView(String id, BigDecimal amount, String category, String method, String description, Instant at) {}

    public record ExpenseRequest(@NotNull BigDecimal amount, String category, String method, String description) {}

    public record TeamView(String id, String name, String phone, String role, String status, java.util.List<String> permissions, String invitationCode) {}

    public record TeamRequest(@NotBlank String name, String phone, String role, String pin) {}

    public record PermissionUpdate(@NotBlank String permission, @NotNull Boolean granted) {}

    public record AuditView(String id, String who, String what, String from, Instant at) {}

    public record EtimsLog(String id, String status, String time) {}

    public record EtimsInfo(boolean connected, boolean configured, String mode, String message, int submittedToday,
                            int accepted, int failed, int pending, List<EtimsLog> logs) {}

    public record AlertView(String kind, String title, String sub) {}

    public record DashboardView(BigDecimal totalSales, int transactions, int itemsSold, BigDecimal grossProfit,
                                BigDecimal cash, BigDecimal mpesa, BigDecimal card, int lowStockCount,
                                BigDecimal refundedTotal, List<AlertView> alerts, List<Integer> trend) {}

    public record NamedAmount(String label, BigDecimal val) {}

    public record NamedAmountName(String name, BigDecimal val) {}

    public record ReportsView(BigDecimal totalSales, int transactions, int itemsSold, BigDecimal avgTransaction,
                              List<NamedAmount> byMethod, List<NamedAmountName> topProducts, List<NamedAmountName> byCashier) {}

    public record LoginRequest(@NotBlank String phone, @NotBlank String pin) {}

    public record RegisterRequest(@NotBlank String phone, @NotBlank String pin, String name, String businessName,
                                  String email, String businessType, String category) {}

    public record UserView(long id, String name, String phone, String role, Long businessId, List<String> permissions) {}

    public record AuthResponse(String token, UserView user) {}

    public record LineView(long id, String rawName, int quantity, BigDecimal unitCost, String barcode,
                           Long matchedProductId, String matchedProductName, Long suggestedProductId,
                           String suggestedProductName, String matchMethod, BigDecimal confidence, boolean removed) {}

    public record ExtractionView(long id, Long documentId, String supplierName, String invoiceNumber, String status,
                                 Long stockReceiptId, Long reviewedBy, Instant reviewedAt, String notes,
                                 Instant createdAt, List<LineView> lines) {}

    public record LineEdit(Long id, String rawName, Integer quantity, BigDecimal unitCost, String barcode,
                           Long matchedProductId, Boolean removed) {}

    public record ExtractionUpdate(String supplierName, String invoiceNumber, List<LineEdit> lines) {}

    public record ReceiptItemView(long productId, String productName, int quantity, BigDecimal unitCost) {}

    public record StockReceiptView(long id, String supplierName, String invoiceNumber, LocalDate receivedDate,
                                   String status, Long approvedBy, Instant approvedAt, String notes,
                                   List<ReceiptItemView> items) {}

    public record ReceiptItemRequest(@NotNull Long productId, @NotNull Integer quantity, @NotNull BigDecimal unitCost) {}

    public record CreateReceiptRequest(String supplierName, String invoiceNumber, List<@Valid ReceiptItemRequest> items) {}

    public record MpesaCallback(@NotBlank String externalRef, @NotBlank String status, String providerReceipt) {}

    public record RefRequest(@NotBlank String externalRef) {}

    public record Ok(boolean success, String message) {}

    public record SupportRequest(@NotBlank String topic, @NotBlank String message, String category, String priority,
                                 java.util.Map<String, String> diagnostics) {}

    public record SupportReply(@NotBlank String message) {}

    public record ProfileUpdate(String name, String email) {}

    public record CredentialChange(@NotBlank String currentPin, @NotBlank String newPin) {}

    public record InviteAccept(@NotBlank String code, @NotBlank String pin) {}

    public record RecoveryRequest(@NotBlank String phone) {}

    public record RecoveryComplete(@NotBlank String code, @NotBlank String newPin) {}

    public record RoleUpdate(@NotBlank String role) {}

    public record OwnerTransfer(@jakarta.validation.constraints.NotNull Long userId, @NotBlank String pin, @NotBlank String confirm, String password) {}

    public record BusinessProfileUpdate(String name, String legalName, String phone, String email, String address,
                                        String businessType, String category, String registrationNumber, String kraPin,
                                        String currency, String timezone, String receiptFooter) {}

    public record CloseBusiness(@NotBlank String pin, @NotBlank String confirm, String password) {}

    public record ShiftOpenRequest(BigDecimal openingCash, String deviceId) {}

    public record ShiftCloseRequest(@NotNull BigDecimal declaredCash, BigDecimal expectedCash, String deviceId) {}

    public record DeviceHeartbeat(String deviceId, String installationId, Long branchId, String name, String appVersion,
                                  Integer pendingSync, Integer failedSync, String lastError, String printerStatus,
                                  String scannerStatus, String oldestPendingAt) {}

    public record SettingsView(Map<String, String> business, Map<String, String> payments, Map<String, String> receipts,
                               Map<String, String> tax) {}
}
