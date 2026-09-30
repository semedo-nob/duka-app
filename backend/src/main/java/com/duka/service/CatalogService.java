package com.duka.service;

import com.duka.domain.*;
import com.duka.repo.*;
import com.duka.security.Access;
import com.duka.security.CurrentUser;
import com.duka.security.Permission;
import com.duka.security.UserPrincipal;
import com.duka.web.ApiException;
import com.duka.web.Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final InventoryBalanceRepository balances;
    private final InventoryMovementRepository movements;
    private final InventoryService inventory;
    private final StockReceiptRepository receipts;
    private final SupplierRepository suppliers;
    private final AuditService audit;
    private final SubscriptionService subscriptions;

    @Transactional(readOnly = true)
    public List<Dto.ProductView> list() {
        UserPrincipal user = CurrentUser.require();
        Map<Long, Integer> stock = balances.findAll().stream()
                .collect(Collectors.toMap(InventoryBalance::getProductId, InventoryBalance::getQuantity));
        Map<Long, Category> byId = categories.findByBusinessIdOrderByNameAsc(user.getBusinessId()).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));
        boolean showCost = showCost();
        return products.findByBusinessId(user.getBusinessId()).stream()
                .map(product -> toView(product, stock.getOrDefault(product.getId(), 0), byId, showCost))
                .toList();
    }

    @Transactional(readOnly = true)
    public Dto.ProductView byBarcode(String code) {
        UserPrincipal user = CurrentUser.require();
        String barcode = code == null ? "" : code.trim();
        Product product = products.findByBusinessIdAndBarcode(user.getBusinessId(), barcode)
                .or(() -> products.findByBusinessIdAndSkuIgnoreCase(user.getBusinessId(), barcode))
                .orElseThrow(() -> new ApiException(404, "No product for barcode " + barcode));
        int stock = balances.findById(product.getId()).map(InventoryBalance::getQuantity).orElse(0);
        return toView(product, stock, categoryMap(user.getBusinessId()), showCost());
    }

    @Transactional
    public Dto.ProductView create(Dto.CreateProductRequest request) {
        UserPrincipal user = CurrentUser.require();
        subscriptions.assertCapacity(user.getBusinessId(), "products", products.findByBusinessId(user.getBusinessId()).size());
        if (request.price().signum() < 0) {
            throw new ApiException(400, "price cannot be negative");
        }
        Category category = resolveCategory(user, request.categoryId(), request.cat());
        Product product = new Product();
        product.setBusinessId(user.getBusinessId());
        product.setName(request.name().trim());
        product.setSku(request.sku() == null || request.sku().isBlank() ? "SKU-" + System.currentTimeMillis() : request.sku().trim());
        if (request.barcode() != null && !request.barcode().isBlank()) {
            product.setBarcode(request.barcode().trim());
        }
        product.setCategory(category);
        applyCommercial(product, request.unit(), request.cost(), request.price(), request.taxRate(), request.reorder(), request.brand(), request.taxCategory(), request.supplierId(), user);
        product.setUpdatedAt(Instant.now());
        Product saved = products.saveAndFlush(product);
        InventoryBalance balance = new InventoryBalance();
        balance.setProductId(saved.getId());
        balance.setQuantity(0);
        balance.setUpdatedAt(Instant.now());
        balances.save(balance);
        audit.log(user.getBusinessId(), user.getName(), "added product " + saved.getName(), saved.getSku(), "PRODUCT", saved.getId().toString(), "");
        return toView(saved, 0, categoryMap(user.getBusinessId()), true);
    }

    @Transactional
    public Dto.ProductView update(long id, Dto.UpdateProductRequest request) {
        UserPrincipal user = CurrentUser.require();
        Product product = owned(id, user);
        BigDecimal previousPrice = product.getPrice();
        if (request.name() != null && !request.name().isBlank()) {
            product.setName(request.name().trim());
        }
        if (request.sku() != null && !request.sku().isBlank()) {
            product.setSku(request.sku().trim());
        }
        if (request.barcode() != null) {
            product.setBarcode(request.barcode().isBlank() ? null : request.barcode().trim());
        }
        if (request.categoryId() != null || (request.cat() != null && !request.cat().isBlank())) {
            product.setCategory(resolveCategory(user, request.categoryId(), request.cat()));
        }
        if (request.price() != null) {
            if (request.price().signum() < 0) {
                throw new ApiException(400, "price cannot be negative");
            }
            product.setPrice(Money.money(request.price()));
        }
        applyCommercial(product, request.unit(), request.cost(), product.getPrice(), request.taxRate(), request.reorder(), request.brand(), request.taxCategory(), request.supplierId(), user);
        if (request.active() != null) {
            product.setActive(request.active());
        }
        product.setUpdatedAt(Instant.now());
        if (request.price() != null && previousPrice.compareTo(product.getPrice()) != 0) {
            audit.log(user.getBusinessId(), user.getName(), "changed price of " + product.getName(),
                    previousPrice + " -> " + product.getPrice(), "PRODUCT", product.getId().toString(), "");
        }
        if (request.active() != null && !request.active()) {
            audit.log(user.getBusinessId(), user.getName(), "deactivated product " + product.getName(), product.getSku(), "PRODUCT", product.getId().toString(), "");
        }
        int stock = balances.findById(product.getId()).map(InventoryBalance::getQuantity).orElse(0);
        return toView(product, stock, categoryMap(user.getBusinessId()), true);
    }

    @Transactional
    public Dto.ProductView receive(long id, Dto.ReceiveRequest request, UserPrincipal user) {
        if (request.qty() == null || request.qty() <= 0) {
            throw new ApiException(400, "qty must be a positive number");
        }
        Product product = products.lockById(id).orElseThrow(() -> new ApiException(404, "Product not found"));
        assertOwned(product, user);
        if (request.cost() != null) {
            if (request.cost().signum() < 0) {
                throw new ApiException(400, "cost cannot be negative");
            }
            product.setCost(Money.money(request.cost()));
            product.setUpdatedAt(Instant.now());
        }
        String supplierName = request.supplier() == null || request.supplier().isBlank() ? "Stock received" : request.supplier();
        StockReceipt receipt = new StockReceipt();
        receipt.setSupplierName(supplierName);
        suppliers.findByBusinessIdAndNameIgnoreCase(user.getBusinessId(), supplierName).ifPresent(supplier -> receipt.setSupplierId(supplier.getId()));
        receipt.setInvoiceNumber("DIRECT-" + System.currentTimeMillis());
        receipt.setStatus(ReceiptStatus.APPROVED);
        receipt.setApprovedBy(user.getId());
        receipt.setApprovedAt(Instant.now());
        receipt.setCreatedBy(user.getId());
        receipt.setNotes("Received directly from the inventory screen");
        StockReceiptItem item = new StockReceiptItem();
        item.setReceipt(receipt);
        item.setProductId(product.getId());
        item.setQuantity(request.qty());
        item.setUnitCost(product.getCost());
        item.setTaxRate(product.getTaxRate());
        receipt.getItems().add(item);
        StockReceipt saved = receipts.saveAndFlush(receipt);
        inventory.apply(product.getId(), MovementType.PURCHASE, request.qty(), product.getCost(),
                "STOCK_RECEIPT", saved.getId().toString(), receipt.getSupplierName(), user.getId());
        audit.log(user.getBusinessId(), user.getName(), "received " + request.qty() + " units of " + product.getName(), "Supplier: " + receipt.getSupplierName(), "PRODUCT", product.getId().toString(), "");
        int stock = balances.findById(product.getId()).map(InventoryBalance::getQuantity).orElse(0);
        return toView(product, stock, categoryMap(user.getBusinessId()), true);
    }

    @Transactional
    public Dto.ProductView adjust(long id, Dto.AdjustRequest request, UserPrincipal user) {
        if (request.delta() == null || request.delta() == 0) {
            throw new ApiException(400, "delta must be a non-zero number");
        }
        Product product = products.lockById(id).orElseThrow(() -> new ApiException(404, "Product not found"));
        assertOwned(product, user);
        String reason = request.reason() == null || request.reason().isBlank() ? "Adjustment" : request.reason();
        MovementType type = movementFor(request.delta(), reason);
        inventory.apply(product.getId(), type, request.delta(), product.getCost(), "ADJUSTMENT", null, reason, user.getId());
        audit.log(user.getBusinessId(), user.getName(), "adjusted stock for " + product.getName(),
                (request.delta() > 0 ? "+" : "") + request.delta() + " units · " + reason, "PRODUCT", product.getId().toString(), "");
        int stock = balances.findById(product.getId()).map(InventoryBalance::getQuantity).orElse(0);
        return toView(product, stock, categoryMap(user.getBusinessId()), true);
    }

    @Transactional(readOnly = true)
    public List<Dto.MovementView> movements(long productId) {
        UserPrincipal user = CurrentUser.require();
        Product product = products.findById(productId).orElseThrow(() -> new ApiException(404, "Product not found"));
        assertOwned(product, user);
        return movements.findTop50ByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(movement -> new Dto.MovementView(
                        movement.getId(),
                        movement.getProductId(),
                        movement.getMovementType().name(),
                        movement.getQuantity(),
                        movement.getNote() == null ? "" : movement.getNote(),
                        movement.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Dto.CategoryView> categories() {
        UserPrincipal user = CurrentUser.require();
        return categories.findByBusinessIdOrderByNameAsc(user.getBusinessId()).stream()
                .map(category -> new Dto.CategoryView(category.getId(), category.getName(), category.getParentId()))
                .toList();
    }

    @Transactional
    public Dto.CategoryView createCategory(Dto.CategoryRequest request) {
        UserPrincipal user = CurrentUser.require();
        Category category = new Category();
        category.setBusinessId(user.getBusinessId());
        category.setName(request.name().trim());
        category.setParentId(parentOrNull(user, request.parentId()));
        Category saved = categories.save(category);
        audit.log(user.getBusinessId(), user.getName(), "added category " + saved.getName(), "", "CATEGORY", saved.getId().toString(), "");
        return new Dto.CategoryView(saved.getId(), saved.getName(), saved.getParentId());
    }

    @Transactional
    public Dto.CategoryView renameCategory(long id, Dto.CategoryRequest request) {
        UserPrincipal user = CurrentUser.require();
        Category category = categories.findByIdAndBusinessId(id, user.getBusinessId()).orElseThrow(() -> new ApiException(404, "Category not found"));
        String previous = category.getName();
        category.setName(request.name().trim());
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new ApiException(400, "A category cannot be its own parent");
            }
            category.setParentId(parentOrNull(user, request.parentId()));
        }
        audit.log(user.getBusinessId(), user.getName(), "renamed category " + previous, category.getName(), "CATEGORY", category.getId().toString(), "");
        return new Dto.CategoryView(category.getId(), category.getName(), category.getParentId());
    }

    @Transactional
    public void deleteCategory(long id) {
        UserPrincipal user = CurrentUser.require();
        Category category = categories.findByIdAndBusinessId(id, user.getBusinessId()).orElseThrow(() -> new ApiException(404, "Category not found"));
        if (products.countByCategoryId(id) > 0) {
            throw new ApiException(409, "Move products out of this category before deleting it");
        }
        if (categories.countByParentId(id) > 0) {
            throw new ApiException(409, "Delete or move the subcategories first");
        }
        categories.delete(category);
        audit.log(user.getBusinessId(), user.getName(), "deleted category " + category.getName(), "", "CATEGORY", String.valueOf(id), "");
    }

    private void applyCommercial(Product product, String unit, BigDecimal cost, BigDecimal price, BigDecimal taxRate, Integer reorder,
                                 String brand, String taxCategory, Long supplierId, UserPrincipal user) {
        if (unit != null && !unit.isBlank()) {
            product.setUnit(unit.trim());
        } else if (product.getUnit() == null) {
            product.setUnit("each");
        }
        if (cost != null) {
            if (cost.signum() < 0) {
                throw new ApiException(400, "cost cannot be negative");
            }
            product.setCost(Money.money(cost));
        } else if (product.getCost() == null) {
            product.setCost(Money.money(price.multiply(new BigDecimal("0.85"))));
        }
        if (price != null && product.getPrice() == null) {
            product.setPrice(Money.money(price));
        }
        if (taxRate != null) {
            product.setTaxRate(taxRate);
        } else if (product.getTaxRate() == null) {
            product.setTaxRate(new BigDecimal("0.1600"));
        }
        if (reorder != null) {
            product.setReorderLevel(reorder);
        }
        if (brand != null) {
            product.setBrand(brand.trim());
        }
        if (taxCategory != null && !taxCategory.isBlank()) {
            product.setTaxCategory(taxCategory.trim());
        }
        if (supplierId != null) {
            suppliers.findByIdAndBusinessId(supplierId, user.getBusinessId()).orElseThrow(() -> new ApiException(404, "Supplier not found"));
            product.setSupplierId(supplierId);
        }
    }

    private Category resolveCategory(UserPrincipal user, Long categoryId, String name) {
        if (categoryId != null) {
            return categories.findByIdAndBusinessId(categoryId, user.getBusinessId()).orElseThrow(() -> new ApiException(404, "Category not found"));
        }
        String label = name == null || name.isBlank() ? "General" : name.trim();
        return categories.findByBusinessIdAndNameIgnoreCaseAndParentIdIsNull(user.getBusinessId(), label).orElseGet(() -> {
            Category created = new Category();
            created.setBusinessId(user.getBusinessId());
            created.setName(label);
            return categories.save(created);
        });
    }

    private Long parentOrNull(UserPrincipal user, Long parentId) {
        if (parentId == null) {
            return null;
        }
        categories.findByIdAndBusinessId(parentId, user.getBusinessId()).orElseThrow(() -> new ApiException(404, "Parent category not found"));
        return parentId;
    }

    private Product owned(long id, UserPrincipal user) {
        Product product = products.findById(id).orElseThrow(() -> new ApiException(404, "Product not found"));
        assertOwned(product, user);
        return product;
    }

    private void assertOwned(Product product, UserPrincipal user) {
        if (user.getBusinessId() != null && product.getBusinessId() != null && !user.getBusinessId().equals(product.getBusinessId())) {
            throw new ApiException(404, "Product not found");
        }
    }

    private Map<Long, Category> categoryMap(Long businessId) {
        return categories.findByBusinessIdOrderByNameAsc(businessId).stream().collect(Collectors.toMap(Category::getId, category -> category));
    }

    private static boolean showCost() {
        return Access.has(Permission.PRODUCT_EDIT) || Access.has(Permission.REPORT_FINANCIAL) || Access.has(Permission.PURCHASE_VIEW);
    }

    private static MovementType movementFor(int delta, String reason) {
        String normalized = reason.toLowerCase();
        if (delta < 0 && normalized.contains("damage")) {
            return MovementType.DAMAGE;
        }
        if (delta < 0 && (normalized.contains("lost") || normalized.contains("loss"))) {
            return MovementType.LOSS;
        }
        if (delta > 0 && normalized.contains("opening")) {
            return MovementType.OPENING_BALANCE;
        }
        return MovementType.STOCK_ADJUSTMENT;
    }

    private static Dto.ProductView toView(Product product, int stock, Map<Long, Category> categories, boolean showCost) {
        Category category = product.getCategory();
        String parent = null;
        if (category.getParentId() != null) {
            Category parentCategory = categories.get(category.getParentId());
            parent = parentCategory == null ? null : parentCategory.getName();
        }
        return new Dto.ProductView(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getBarcode(),
                product.getPrice(),
                showCost ? product.getCost() : null,
                stock,
                product.getReorderLevel(),
                category.getName(),
                product.getEmoji(),
                product.getColor(),
                product.getUnit(),
                product.getTaxRate(),
                product.isActive(),
                product.getBrand(),
                product.getTaxCategory(),
                showCost ? product.getSupplierId() : null,
                category.getId(),
                parent);
    }
}
