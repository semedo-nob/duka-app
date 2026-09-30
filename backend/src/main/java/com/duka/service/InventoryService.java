package com.duka.service;

import com.duka.domain.InventoryBalance;
import com.duka.domain.InventoryMovement;
import com.duka.domain.MovementType;
import com.duka.repo.AppSettingRepository;
import com.duka.repo.InventoryBalanceRepository;
import com.duka.repo.InventoryMovementRepository;
import com.duka.repo.ProductRepository;
import com.duka.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final ProductRepository products;
    private final InventoryBalanceRepository balances;
    private final InventoryMovementRepository movements;
    private final AppSettingRepository settings;

    @Transactional
    public void apply(long productId, MovementType type, int signedQty, BigDecimal unitCost,
                      String referenceType, String referenceId, String note, Long userId) {
        if (signedQty == 0) {
            throw new ApiException(400, "quantity must be non-zero");
        }
        var product = products.lockById(productId)
                .orElseThrow(() -> new ApiException(404, "Product not found"));
        InventoryBalance balance = balances.lockByProductId(productId).orElseGet(() -> {
            InventoryBalance created = new InventoryBalance();
            created.setProductId(productId);
            created.setQuantity(0);
            created.setUpdatedAt(Instant.now());
            return balances.saveAndFlush(created);
        });
        int next = balance.getQuantity() + signedQty;
        if (preventNegative() && next < 0) {
            throw new ApiException(409, "Not enough stock for " + product.getName());
        }
        balance.setQuantity(next);
        balance.setUpdatedAt(Instant.now());

        InventoryMovement movement = new InventoryMovement();
        movement.setProductId(productId);
        movement.setMovementType(type);
        movement.setQuantity(signedQty);
        movement.setUnitCost(unitCost);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setNote(note);
        movement.setCreatedBy(userId);
        movements.save(movement);
    }

    public boolean preventNegative() {
        return settings.findById("prevent_negative_stock")
                .map(setting -> !"false".equalsIgnoreCase(setting.getValue()))
                .orElse(true);
    }
}
