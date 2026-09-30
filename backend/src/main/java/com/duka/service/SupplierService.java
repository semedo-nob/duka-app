package com.duka.service;

import com.duka.domain.*;
import com.duka.repo.*;
import com.duka.security.UserPrincipal;
import com.duka.web.ApiException;
import com.duka.web.Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SupplierRepository suppliers;
    private final SupplierOfferRepository offers;
    private final ProductRepository products;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository lines;
    private final StockReceiptRepository receipts;
    private final InventoryService inventory;
    private final AuditService audit;
    private final SubscriptionService subscriptions;

    @Transactional(readOnly = true)
    public List<Dto.SupplierView> list(UserPrincipal user, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        return suppliers.findByBusinessIdOrderByNameAsc(user.getBusinessId()).stream()
                .filter(supplier -> needle.isBlank()
                        || supplier.getName().toLowerCase().contains(needle)
                        || supplier.getPhone().toLowerCase().contains(needle)
                        || supplier.getContact().toLowerCase().contains(needle)
                        || supplier.getCategory().toLowerCase().contains(needle))
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public Dto.SupplierDetail get(long id, UserPrincipal user) {
        Supplier supplier = owned(id, user);
        List<Dto.SupplierOfferView> offerViews = offers.findBySupplierIdOrderByIdAsc(id).stream().map(offer -> {
            String name = products.findById(offer.getProductId()).map(Product::getName).orElse("Product");
            return new Dto.SupplierOfferView(offer.getId(), offer.getProductId(), name, offer.getSupplierSku(), offer.getAlias(), offer.getUnitCost());
        }).toList();
        List<Dto.SupplierHistory> history = new ArrayList<>();
        for (PurchaseOrder order : orders.findByBusinessIdAndSupplierIdOrderByCreatedAtDesc(user.getBusinessId(), id)) {
            history.add(new Dto.SupplierHistory(0, "PURCHASE_ORDER", order.getId(), order.getTotal(), order.getStatus(), order.getCreatedAt()));
        }
        for (StockReceipt receipt : receipts.findBySupplierIdOrderByCreatedAtDesc(id)) {
            history.add(new Dto.SupplierHistory(receipt.getId(), "STOCK_RECEIPT", receipt.getInvoiceNumber(), null, receipt.getStatus().name(), receipt.getCreatedAt()));
        }
        history.sort(Comparator.comparing(Dto.SupplierHistory::at, Comparator.nullsLast(Comparator.reverseOrder())));
        return new Dto.SupplierDetail(view(supplier), offerViews, history);
    }

    @Transactional
    public Dto.SupplierView create(Dto.SupplierRequest request, UserPrincipal user) {
        Supplier supplier = new Supplier();
        supplier.setBusinessId(user.getBusinessId());
        fill(supplier, request);
        Supplier saved = suppliers.save(supplier);
        audit.log(user.getBusinessId(), user.getName(), "added supplier " + saved.getName(), saved.getPhone(), "SUPPLIER", saved.getId().toString(), "");
        return view(saved);
    }

    @Transactional
    public Dto.SupplierView update(long id, Dto.SupplierRequest request, UserPrincipal user) {
        Supplier supplier = owned(id, user);
        BigDecimal before = supplier.getBalance();
        fill(supplier, request);
        if (request.active() != null) {
            supplier.setActive(request.active());
        }
        audit.log(user.getBusinessId(), user.getName(), "updated supplier " + supplier.getName(),
                "balance " + before + " -> " + supplier.getBalance(), "SUPPLIER", supplier.getId().toString(), "");
        return view(supplier);
    }

    @Transactional
    public Dto.SupplierOfferView saveOffer(long supplierId, Dto.SupplierOfferRequest request, UserPrincipal user) {
        Supplier supplier = owned(supplierId, user);
        Product product = products.findById(request.productId()).orElseThrow(() -> new ApiException(404, "Product not found"));
        if (!user.getBusinessId().equals(product.getBusinessId())) {
            throw new ApiException(404, "Product not found");
        }
        SupplierOffer offer = offers.findBySupplierIdAndProductId(supplierId, product.getId()).orElseGet(SupplierOffer::new);
        offer.setSupplierId(supplier.getId());
        offer.setProductId(product.getId());
        offer.setSupplierSku(request.supplierSku() == null ? "" : request.supplierSku().trim());
        offer.setAlias(request.alias() == null ? "" : request.alias().trim());
        offer.setUnitCost(Money.money(request.unitCost() == null ? product.getCost() : request.unitCost()));
        offer.setActive(true);
        SupplierOffer saved = offers.save(offer);
        if (product.getSupplierId() == null) {
            product.setSupplierId(supplier.getId());
        }
        audit.log(user.getBusinessId(), user.getName(), "linked " + product.getName() + " to " + supplier.getName(),
                saved.getSupplierSku(), "SUPPLIER", supplier.getId().toString(), "");
        return new Dto.SupplierOfferView(saved.getId(), product.getId(), product.getName(), saved.getSupplierSku(), saved.getAlias(), saved.getUnitCost());
    }

    @Transactional
    public Dto.PurchaseOrderView createOrder(Dto.PurchaseOrderRequest request, UserPrincipal user) {
        requirePurchasing(user);
        Supplier supplier = null;
        if (request.supplierId() != null) {
            supplier = owned(request.supplierId(), user);
        } else if (request.supplier() != null && !request.supplier().isBlank()) {
            supplier = suppliers.findByBusinessIdAndNameIgnoreCase(user.getBusinessId(), request.supplier().trim()).orElse(null);
        }
        PurchaseOrder order = new PurchaseOrder();
        order.setId("PO-" + (1044 + orders.count()));
        order.setBusinessId(user.getBusinessId());
        order.setSupplier(supplier == null ? (request.supplier() == null || request.supplier().isBlank() ? "Unknown supplier" : request.supplier().trim()) : supplier.getName());
        order.setSupplierId(supplier == null ? null : supplier.getId());
        order.setStatus("Awaiting delivery");
        List<Dto.PurchaseOrderLineRequest> requested = request.lines() == null ? List.of() : request.lines();
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseOrderLine> stored = new ArrayList<>();
        for (Dto.PurchaseOrderLineRequest line : requested) {
            if (line.productId() == null || line.qty() == null || line.qty() <= 0) {
                continue;
            }
            Product product = products.findById(line.productId()).orElseThrow(() -> new ApiException(404, "Product not found"));
            if (!user.getBusinessId().equals(product.getBusinessId())) {
                throw new ApiException(404, "Product not found");
            }
            BigDecimal cost = Money.money(line.unitCost() == null ? product.getCost() : line.unitCost());
            PurchaseOrderLine storedLine = new PurchaseOrderLine();
            storedLine.setProductId(product.getId());
            storedLine.setQuantity(line.qty());
            storedLine.setUnitCost(cost);
            stored.add(storedLine);
            count += line.qty();
            total = total.add(cost.multiply(BigDecimal.valueOf(line.qty())));
        }
        if (stored.isEmpty()) {
            order.setItems(request.items() == null ? 1 : request.items());
            order.setTotal(Money.money(request.total()));
        } else {
            order.setItems(count);
            order.setTotal(Money.money(total));
        }
        orders.save(order);
        for (PurchaseOrderLine line : stored) {
            line.setOrderId(order.getId());
            lines.save(line);
        }
        audit.log(user.getBusinessId(), user.getName(), "created purchase order " + order.getId(), "Supplier: " + order.getSupplier(), "PURCHASE_ORDER", order.getId(), "");
        return view(order);
    }

    @Transactional(readOnly = true)
    public List<Dto.PurchaseOrderView> orders(UserPrincipal user) {
        return orders.findByBusinessIdOrderByCreatedAtDesc(user.getBusinessId()).stream().map(this::view).toList();
    }

    @Transactional
    public Dto.PurchaseOrderView receive(String id, UserPrincipal user) {
        requirePurchasing(user);
        PurchaseOrder order = orders.findById(id).orElseThrow(() -> new ApiException(404, "Purchase order not found"));
        if (!user.getBusinessId().equals(order.getBusinessId())) {
            throw new ApiException(404, "Purchase order not found");
        }
        if (order.isStockApplied()) {
            return view(order);
        }
        List<PurchaseOrderLine> orderLines = lines.findByOrderId(id);
        if (orderLines.isEmpty()) {
            throw new ApiException(400, "This order has no product lines, so stock cannot be received from it");
        }
        StockReceipt receipt = new StockReceipt();
        receipt.setSupplierId(order.getSupplierId());
        receipt.setSupplierName(order.getSupplier());
        receipt.setInvoiceNumber(order.getId());
        receipt.setStatus(ReceiptStatus.APPROVED);
        receipt.setApprovedBy(user.getId());
        receipt.setApprovedAt(java.time.Instant.now());
        receipt.setCreatedBy(user.getId());
        receipt.setNotes("Received from purchase order " + order.getId());
        for (PurchaseOrderLine line : orderLines) {
            StockReceiptItem item = new StockReceiptItem();
            item.setReceipt(receipt);
            item.setProductId(line.getProductId());
            item.setQuantity(line.getQuantity());
            item.setUnitCost(line.getUnitCost());
            item.setTaxRate(BigDecimal.ZERO);
            receipt.getItems().add(item);
        }
        StockReceipt saved = receipts.saveAndFlush(receipt);
        for (PurchaseOrderLine line : orderLines) {
            inventory.apply(line.getProductId(), MovementType.PURCHASE, line.getQuantity(), line.getUnitCost(),
                    "PURCHASE_ORDER", order.getId(), order.getSupplier(), user.getId());
        }
        order.setStockApplied(true);
        order.setStatus("Delivered");
        audit.log(user.getBusinessId(), user.getName(), "received purchase order " + order.getId(), saved.getId().toString(), "PURCHASE_ORDER", order.getId(), "");
        return view(order);
    }

    private void fill(Supplier supplier, Dto.SupplierRequest request) {
        supplier.setName(request.name().trim());
        String phone = request.phone() == null || request.phone().isBlank() ? (request.contact() == null ? "" : request.contact()) : request.phone();
        supplier.setPhone(phone);
        supplier.setContact(request.contact() == null || request.contact().isBlank() ? phone : request.contact());
        supplier.setEmail(request.email() == null ? "" : request.email().trim());
        supplier.setAddress(request.address() == null ? "" : request.address().trim());
        supplier.setCategory(request.category() == null ? "" : request.category().trim());
        supplier.setNotes(request.notes() == null ? "" : request.notes().trim());
        supplier.setProducts(request.products() == null ? "" : request.products());
        if (request.balance() != null) {
            if (request.balance().signum() < 0) {
                throw new ApiException(400, "Recorded payable cannot be negative");
            }
            supplier.setBalance(Money.money(request.balance()));
        }
    }

    private Supplier owned(long id, UserPrincipal user) {
        return suppliers.findByIdAndBusinessId(id, user.getBusinessId()).orElseThrow(() -> new ApiException(404, "Supplier not found"));
    }

    private void requirePurchasing(UserPrincipal user) {
        if (!Boolean.TRUE.equals(subscriptions.entitlements(user.getBusinessId()).get("purchasing"))) {
            throw new ApiException(403, "Purchase orders are not on this plan");
        }
    }

    private Dto.SupplierView view(Supplier supplier) {
        return new Dto.SupplierView(supplier.getId(), supplier.getName(), supplier.getContact(), supplier.getProducts(),
                supplier.getPhone(), supplier.getEmail(), supplier.getAddress(), supplier.getCategory(), supplier.getNotes(),
                supplier.isActive(), supplier.getBalance());
    }

    private Dto.PurchaseOrderView view(PurchaseOrder order) {
        return new Dto.PurchaseOrderView(order.getId(), order.getSupplier(), order.getItems(), order.getTotal(), order.getStatus(), order.getSupplierId(), order.isStockApplied());
    }
}
