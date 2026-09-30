package com.duka.service;

import com.duka.domain.*;
import com.duka.integrations.mpesa.MpesaGateway;
import com.duka.repo.*;
import com.duka.security.CurrentUser;
import com.duka.security.UserPrincipal;
import com.duka.web.ApiException;
import com.duka.web.Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleService {
    private final SaleRepository sales;
    private final PaymentRepository payments;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SubscriptionService subscriptions;
    private final InventoryService inventory;
    private final EtimsService etims;
    private final MpesaGateway mpesa;
    private final AuditService audit;
    private final DeviceRegistrationRepository devices;
    private final BusinessRepository businesses;

    @Transactional
    public Dto.SaleView create(Dto.CreateSaleRequest request, String idempotencyKey, UserPrincipal user,
                                String deviceId, String installationId, String clientSaleNo, String businessId) {
        String key = blankToNull(idempotencyKey);
        String businessKey = businessKey(user);
        if (key != null) {
            var existing = sales.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                if (existing.get().getBusinessId() != null && !businessKey.equals(existing.get().getBusinessId())) {
                    throw new ApiException(409, "This sale was already submitted");
                }
                return toView(existing.get());
            }
        }
        String headerBusiness = blankToNull(businessId);
        if (headerBusiness != null && !"unassigned".equals(headerBusiness) && !businessKey.equals(headerBusiness)) {
            throw new ApiException(403, "This device is registered to a different business");
        }
        devices.findById(blankToNull(deviceId) == null ? "" : deviceId).ifPresent(device -> {
            if (device.isRevoked()) {
                throw new ApiException(403, "This device has been revoked");
            }
        });
        requireCanSell(user);
        if (request.items() == null || request.items().isEmpty()) {
            throw new ApiException(400, "items are required");
        }
        String method = request.method().trim().toLowerCase();
        List<Long> productIds = request.items().stream().map(Dto.SaleItemRequest::productId).distinct().sorted().toList();
        for (Long productId : productIds) {
            products.lockById(productId).orElseThrow(() -> new ApiException(404, "Product not found"));
        }

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        List<SaleItem> lines = new ArrayList<>();
        int lineNo = 0;
        for (Dto.SaleItemRequest item : request.items()) {
            if (item.qty() <= 0) {
                throw new ApiException(400, "quantity must be positive");
            }
            if (item.price().signum() < 0) {
                throw new ApiException(400, "price cannot be negative");
            }
            Product product = products.findById(item.productId()).orElseThrow(() -> new ApiException(404, "Product not found"));
            if (product.getBusinessId() != null && !user.getBusinessId().equals(product.getBusinessId())) {
                throw new ApiException(404, "Product not found");
            }
            if (!product.isActive()) {
                throw new ApiException(400, product.getName() + " is inactive");
            }
            BigDecimal lineGross = Money.money(item.price()).multiply(BigDecimal.valueOf(item.qty()));
            gross = gross.add(lineGross);
            tax = tax.add(Money.includedTax(lineGross, product.getTaxRate()));
            SaleItem line = new SaleItem();
            line.setProductId(product.getId());
            line.setLineNo(lineNo++);
            line.setName(item.name() == null || item.name().isBlank() ? product.getName() : item.name());
            line.setQuantity(item.qty());
            line.setUnitPrice(Money.money(item.price()));
            line.setUnitCost(product.getCost());
            lines.add(line);
        }
        BigDecimal discount = Money.money(request.discount());
        if (discount.compareTo(gross) > 0) {
            throw new ApiException(400, "discount cannot exceed the sale total");
        }
        BigDecimal total = Money.money(gross.subtract(discount));
        if (gross.signum() > 0) {
            tax = tax.multiply(total).divide(gross, 2, java.math.RoundingMode.HALF_UP);
        }
        BigDecimal subtotal = total.subtract(tax);

        Customer customer = null;
        if (request.customerId() != null) {
            customer = customers.findByIdAndBusinessId(request.customerId(), user.getBusinessId()).orElseThrow(() -> new ApiException(404, "Customer not found"));
        }
        if ("credit".equals(method)) {
            if (customer == null) {
                throw new ApiException(400, "Credit sales need a customer");
            }
            if (!Boolean.TRUE.equals(subscriptions.entitlements(user.getBusinessId()).get("credit"))) {
                throw new ApiException(403, "Credit sales are not on this plan");
            }
        }

        List<PlannedPayment> planned = planPayments(method, total, request);
        boolean pending = planned.stream().anyMatch(payment -> payment.status == PaymentStatus.PENDING);

        Sale sale = new Sale();
        sale.setMethod(method);
        sale.setSubtotal(subtotal);
        sale.setDiscount(discount);
        sale.setTax(tax);
        sale.setTotal(total);
        sale.setCustomerId(customer == null ? null : customer.getId());
        sale.setIdempotencyKey(key);
        sale.setCreatedBy(user.getId());
        sale.setDeviceId(blankToNull(deviceId));
        sale.setInstallationId(blankToNull(installationId));
        sale.setClientSaleNo(blankToNull(clientSaleNo));
        sale.setBusinessId(businessKey);
        sale.setStatus(pending ? SaleStatus.AWAITING_PAYMENT : SaleStatus.COMPLETED);
        for (SaleItem line : lines) {
            line.setSale(sale);
            sale.getItems().add(line);
        }
        Sale saved = sales.saveAndFlush(sale);
        for (PlannedPayment plannedPayment : planned) {
            Payment payment = new Payment();
            payment.setSaleId(saved.getId());
            payment.setMethod(plannedPayment.method);
            payment.setAmount(plannedPayment.amount);
            payment.setTendered(plannedPayment.tendered);
            payment.setStatus(plannedPayment.status);
            payment.setProvider(plannedPayment.provider);
            payment.setExternalRef(plannedPayment.externalRef);
            payments.save(payment);
        }
        if (!pending) {
            applyStock(saved, user.getId());
            if ("credit".equals(method) && customer != null) {
                customer.setBalance(customer.getBalance().add(total));
            }
            etims.queue(saved);
        }
        audit.log(user.getName(), "recorded sale #" + saved.getId(), saved.getMethod() + " · KSh " + saved.getTotal());
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<Dto.SaleView> recent() {
        return sales.findTop50ByBusinessIdOrderByCreatedAtDesc(businessKey(CurrentUser.require())).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public Dto.SaleView get(long id) {
        Sale sale = sales.findById(id).orElseThrow(() -> new ApiException(404, "Sale not found"));
        if (sale.getBusinessId() != null && !sale.getBusinessId().equals(businessKey(CurrentUser.require()))) {
            throw new ApiException(404, "Sale not found");
        }
        return toView(sale);
    }

    @Transactional
    public Dto.SaleView confirmProvider(String externalRef, boolean success, String providerReceipt) {
        Payment payment = payments.lockByExternalRef(externalRef)
                .orElseThrow(() -> new ApiException(404, "Unknown payment reference"));
        Sale sale = sales.lockById(payment.getSaleId()).orElseThrow(() -> new ApiException(404, "Sale not found"));
        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.FAILED) {
            return toView(sale);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return toView(sale);
        }
        payment.setUpdatedAt(Instant.now());
        if (!success) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Provider reported a failed payment");
            boolean anyPending = payments.findBySaleId(sale.getId()).stream().anyMatch(row -> row.getStatus() == PaymentStatus.PENDING);
            if (!anyPending && !sale.isStockApplied()) {
                sale.setStatus(SaleStatus.VOID);
            }
            return toView(sale);
        }
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setProviderReceipt(providerReceipt);
        payments.saveAndFlush(payment);
        boolean allCompleted = payments.findBySaleId(sale.getId()).stream().allMatch(row -> row.getStatus() == PaymentStatus.COMPLETED);
        if (allCompleted && !sale.isStockApplied()) {
            sale.setStatus(SaleStatus.COMPLETED);
            applyStock(sale, sale.getCreatedBy());
            etims.queue(sale);
        }
        return toView(sale);
    }

    @Transactional
    public Dto.SaleView refund(long id, Dto.RefundRequest request, UserPrincipal user) {
        Sale sale = sales.lockById(id).orElseThrow(() -> new ApiException(404, "Sale not found"));
        if (sale.getBusinessId() != null && !sale.getBusinessId().equals(businessKey(user))) {
            throw new ApiException(404, "Sale not found");
        }
        if (sale.getStatus() != SaleStatus.COMPLETED || !sale.isStockApplied()) {
            throw new ApiException(409, "Only a completed sale can be refunded");
        }
        if (request.itemIndices() == null || request.itemIndices().isEmpty()) {
            throw new ApiException(400, "Select at least one item to refund");
        }
        List<SaleItem> items = sale.getItems().stream().sorted(Comparator.comparingInt(SaleItem::getLineNo)).toList();
        BigDecimal refundedAmount = BigDecimal.ZERO;
        for (Integer index : request.itemIndices()) {
            if (index == null || index < 0 || index >= items.size()) {
                continue;
            }
            SaleItem item = items.get(index);
            if (item.isRefunded()) {
                continue;
            }
            inventory.apply(item.getProductId(), MovementType.SALE_RETURN, item.getQuantity(), item.getUnitCost(),
                    "SALE", sale.getId().toString(), request.reason() == null ? "Refund" : request.reason(), user.getId());
            item.setRefunded(true);
            refundedAmount = refundedAmount.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        sale.setRefunded(items.stream().allMatch(SaleItem::isRefunded));
        final BigDecimal returned = refundedAmount;
        if ("credit".equals(sale.getMethod()) && sale.getCustomerId() != null && returned.signum() > 0) {
            customers.findById(sale.getCustomerId()).ifPresent(customer -> {
                BigDecimal next = customer.getBalance().subtract(returned);
                customer.setBalance(next.signum() < 0 ? BigDecimal.ZERO : next);
            });
        }
        audit.log(user.getName(), "refunded sale #" + sale.getId(), "KSh " + refundedAmount);
        return toView(sale);
    }

    private void applyStock(Sale sale, Long userId) {
        if (sale.isStockApplied()) {
            return;
        }
        for (SaleItem item : sale.getItems()) {
            inventory.apply(item.getProductId(), MovementType.SALE, -item.getQuantity(), item.getUnitCost(),
                    "SALE", sale.getId().toString(), "Sale", userId);
        }
        sale.setStockApplied(true);
    }

    private List<PlannedPayment> planPayments(String method, BigDecimal total, Dto.CreateSaleRequest request) {
        if ("split".equals(method)) {
            if (request.payments() == null || request.payments().isEmpty()) {
                throw new ApiException(400, "Split payments need a breakdown");
            }
            BigDecimal sum = BigDecimal.ZERO;
            List<PlannedPayment> planned = new ArrayList<>();
            for (Dto.PaymentPart part : request.payments()) {
                if (part.amount().signum() < 0) {
                    throw new ApiException(400, "payment amount cannot be negative");
                }
                sum = sum.add(part.amount());
                planned.add(single(part.method(), Money.money(part.amount()), null));
            }
            if (Money.money(sum).compareTo(total) != 0) {
                throw new ApiException(400, "Split payments must add up to the sale total");
            }
            return planned;
        }
        if ("cash".equals(method) && request.tendered() != null && Money.money(request.tendered()).compareTo(total) < 0) {
            throw new ApiException(400, "Cash received is less than the amount due");
        }
        BigDecimal tendered = "cash".equals(method) ? request.tendered() : null;
        return List.of(single(method, total, tendered));
    }

    private PlannedPayment single(String method, BigDecimal amount, BigDecimal tendered) {
        PaymentMethod parsed;
        try {
            parsed = PaymentMethod.from(method);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(400, "Unknown payment method");
        }
        return switch (parsed) {
            case CASH -> new PlannedPayment(parsed, amount, tendered == null ? amount : Money.money(tendered), PaymentStatus.COMPLETED, "CASH", null);
            case CARD -> new PlannedPayment(parsed, amount, null, PaymentStatus.COMPLETED, "MANUAL_CARD", null);
            case CREDIT -> new PlannedPayment(parsed, amount, null, PaymentStatus.COMPLETED, "CUSTOMER_CREDIT", null);
            case MPESA -> {
                MpesaGateway.Initiated initiated = mpesa.initiate(amount, "SALE");
                yield new PlannedPayment(parsed, amount, null, PaymentStatus.PENDING, initiated.provider(), initiated.externalRef());
            }
        };
    }

    private Dto.SaleView toView(Sale sale) {
        List<Dto.SaleItemView> items = sale.getItems().stream()
                .sorted(Comparator.comparingInt(SaleItem::getLineNo))
                .map(item -> new Dto.SaleItemView(item.getProductId(), item.getName(), item.getQuantity(), item.getUnitPrice(), item.isRefunded()))
                .toList();
        List<Payment> salePayments = payments.findBySaleId(sale.getId());
        String paymentStatus = aggregate(salePayments);
        Payment highlighted = salePayments.stream()
                .filter(payment -> payment.getMethod() == PaymentMethod.MPESA)
                .findFirst()
                .orElse(salePayments.isEmpty() ? null : salePayments.get(0));
        String customerName = sale.getCustomerId() == null ? null
                : customers.findById(sale.getCustomerId()).map(Customer::getName).orElse(null);
        return new Dto.SaleView(
                sale.getId(),
                items,
                sale.getTotal(),
                sale.getMethod(),
                sale.getCreatedAt(),
                sale.getCustomerId(),
                customerName,
                etims.labelFor(sale.getId()),
                sale.isRefunded(),
                sale.getStatus().name(),
                paymentStatus,
                highlighted == null ? null : highlighted.getId(),
                highlighted == null ? null : highlighted.getExternalRef(),
                sale.getDiscount(),
                sale.getTax(),
                sale.getSubtotal());
    }

    private void requireCanSell(UserPrincipal user) {
        Business business = businesses.findById(user.getBusinessId())
                .orElseThrow(() -> new ApiException(404, "Business not found"));
        BusinessStatus status = business.getStatus();
        if (status == BusinessStatus.PENDING_APPROVAL || status == BusinessStatus.PENDING_VERIFICATION || status == BusinessStatus.REGISTERED) {
            throw new ApiException(403, "This business is waiting for approval. Selling stays paused.");
        }
        if (status == BusinessStatus.SUSPENDED) {
            throw new ApiException(403, "This business is suspended. Selling stays paused.");
        }
        if (status == BusinessStatus.CLOSED || status == BusinessStatus.DEACTIVATED || status == BusinessStatus.CANCELLED) {
            throw new ApiException(403, "This business is closed. Selling stays paused.");
        }
    }

    private static String aggregate(List<Payment> salePayments) {
        if (salePayments.isEmpty()) {
            return "COMPLETED";
        }
        if (salePayments.stream().anyMatch(payment -> payment.getStatus() == PaymentStatus.PENDING)) {
            return "PENDING";
        }
        if (salePayments.stream().anyMatch(payment -> payment.getStatus() == PaymentStatus.FAILED)) {
            return "FAILED";
        }
        return "COMPLETED";
    }

    private static String businessKey(UserPrincipal user) {
        if (user.getBusinessId() == null) {
            throw new ApiException(403, "This login is not attached to a business");
        }
        return String.valueOf(user.getBusinessId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PlannedPayment(PaymentMethod method, BigDecimal amount, BigDecimal tendered, PaymentStatus status,
                                  String provider, String externalRef) {}
}
