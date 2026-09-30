package com.duka.service;

import com.duka.domain.*;
import com.duka.repo.ProductRepository;
import com.duka.repo.StockReceiptRepository;
import com.duka.repo.SupplierRepository;
import com.duka.security.UserPrincipal;
import com.duka.web.ApiException;
import com.duka.web.Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockReceiptService {
    private final StockReceiptRepository receipts;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final AuditService audit;
    private final SupplierRepository suppliers;

    @Transactional(readOnly = true)
    public List<Dto.StockReceiptView> list() {
        return receipts.findTop50ByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    @Transactional
    public Dto.StockReceiptView create(Dto.CreateReceiptRequest request, UserPrincipal user) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new ApiException(400, "A stock receipt needs at least one line");
        }
        StockReceipt receipt = base(request.supplierName(), request.invoiceNumber(), user);
        receipt.setStatus(ReceiptStatus.REVIEW);
        for (Dto.ReceiptItemRequest item : request.items()) {
            addItem(receipt, item.productId(), item.quantity(), item.unitCost());
        }
        return toView(receipts.save(receipt));
    }

    @Transactional
    public Dto.StockReceiptView approve(long id, UserPrincipal user) {
        StockReceipt receipt = receipts.findById(id).orElseThrow(() -> new ApiException(404, "Stock receipt not found"));
        if (receipt.getStatus() == ReceiptStatus.APPROVED) {
            return toView(receipt);
        }
        if (receipt.getStatus() == ReceiptStatus.REJECTED) {
            throw new ApiException(409, "This receipt was rejected");
        }
        if (receipt.getItems().isEmpty()) {
            throw new ApiException(400, "This receipt has no lines");
        }
        post(receipt, user);
        audit.log(user.getName(), "approved stock receipt #" + receipt.getId(), receipt.getInvoiceNumber());
        return toView(receipt);
    }

    @Transactional
    public Dto.StockReceiptView reject(long id, UserPrincipal user) {
        StockReceipt receipt = receipts.findById(id).orElseThrow(() -> new ApiException(404, "Stock receipt not found"));
        if (receipt.getStatus() == ReceiptStatus.APPROVED) {
            throw new ApiException(409, "An approved receipt cannot be rejected");
        }
        receipt.setStatus(ReceiptStatus.REJECTED);
        audit.log(user.getName(), "rejected stock receipt #" + receipt.getId(), "");
        return toView(receipt);
    }

    @Transactional
    public StockReceipt approveNew(String supplierName, String invoiceNumber, Long documentId, List<Line> lines, UserPrincipal user) {
        StockReceipt receipt = base(supplierName, invoiceNumber, user);
        receipt.setDocumentId(documentId);
        receipt.setStatus(ReceiptStatus.REVIEW);
        for (Line line : lines) {
            addItem(receipt, line.productId(), line.quantity(), line.unitCost());
        }
        receipts.saveAndFlush(receipt);
        post(receipt, user);
        return receipt;
    }

    private void post(StockReceipt receipt, UserPrincipal user) {
        List<Long> productIds = receipt.getItems().stream().map(StockReceiptItem::getProductId).distinct().sorted().toList();
        for (Long productId : productIds) {
            products.lockById(productId).orElseThrow(() -> new ApiException(404, "Product not found"));
        }
        for (StockReceiptItem item : new ArrayList<>(receipt.getItems())) {
            Product product = products.findById(item.getProductId()).orElseThrow(() -> new ApiException(404, "Product not found"));
            product.setCost(item.getUnitCost());
            product.setUpdatedAt(Instant.now());
            inventory.apply(product.getId(), MovementType.PURCHASE, item.getQuantity(), item.getUnitCost(),
                    "STOCK_RECEIPT", receipt.getId().toString(), "Approved receipt " + receipt.getInvoiceNumber(), user.getId());
        }
        receipt.setStatus(ReceiptStatus.APPROVED);
        receipt.setApprovedBy(user.getId());
        receipt.setApprovedAt(Instant.now());
    }

    private StockReceipt base(String supplierName, String invoiceNumber, UserPrincipal user) {
        StockReceipt receipt = new StockReceipt();
        receipt.setSupplierName(supplierName);
        if (supplierName != null && !supplierName.isBlank() && user.getBusinessId() != null) {
            suppliers.findByBusinessIdAndNameIgnoreCase(user.getBusinessId(), supplierName.trim())
                    .ifPresent(supplier -> receipt.setSupplierId(supplier.getId()));
        }
        receipt.setInvoiceNumber(invoiceNumber);
        receipt.setCreatedBy(user.getId());
        return receipt;
    }

    private void addItem(StockReceipt receipt, Long productId, Integer quantity, java.math.BigDecimal unitCost) {
        if (quantity == null || quantity <= 0) {
            throw new ApiException(400, "quantity must be positive");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw new ApiException(400, "unit cost cannot be negative");
        }
        Product product = products.findById(productId).orElseThrow(() -> new ApiException(404, "Product not found"));
        StockReceiptItem item = new StockReceiptItem();
        item.setReceipt(receipt);
        item.setProductId(product.getId());
        item.setQuantity(quantity);
        item.setUnitCost(Money.money(unitCost));
        item.setTaxRate(product.getTaxRate());
        receipt.getItems().add(item);
    }

    private Dto.StockReceiptView toView(StockReceipt receipt) {
        List<Dto.ReceiptItemView> items = receipt.getItems().stream().map(item -> {
            String name = products.findById(item.getProductId()).map(Product::getName).orElse("Product");
            return new Dto.ReceiptItemView(item.getProductId(), name, item.getQuantity(), item.getUnitCost());
        }).toList();
        return new Dto.StockReceiptView(receipt.getId(), receipt.getSupplierName(), receipt.getInvoiceNumber(),
                receipt.getReceivedDate(), receipt.getStatus().name(), receipt.getApprovedBy(), receipt.getApprovedAt(),
                receipt.getNotes(), items);
    }

    public record Line(long productId, int quantity, java.math.BigDecimal unitCost) {}
}
