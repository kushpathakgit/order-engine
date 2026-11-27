package com.orderengine.controller;

import com.orderengine.dto.InventoryReconciliationResponse;
import com.orderengine.service.InventoryReconciliationService;
import com.orderengine.service.InventoryRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.orderengine.service.InventoryRecoveryService;

@RestController
@RequestMapping("/api/inventory")
public class InventoryReconciliationController {

    private final InventoryReconciliationService reconciliationService;
    private InventoryRecoveryService recoveryService;

    public InventoryReconciliationController(
            InventoryReconciliationService reconciliationService, InventoryRecoveryService recoveryService) {
        this.reconciliationService = reconciliationService;
        this.recoveryService = recoveryService;
    }

    @GetMapping("/{productId}/reconciliation")
    public ResponseEntity<InventoryReconciliationResponse> reconcile(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                reconciliationService.reconcile(productId)
        );
    }
    @PostMapping("/{productId}/recover")
    public ResponseEntity<String> recover(
            @PathVariable Long productId) {

        recoveryService.recoverProductInventory(productId);

        return ResponseEntity.ok(
                "Redis inventory recovered for product: " + productId);
    }
}