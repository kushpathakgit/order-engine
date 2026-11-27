package com.orderengine.controller;

import com.orderengine.dto.InventoryReconciliationResponse;
import com.orderengine.service.InventoryReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryReconciliationController {

    private final InventoryReconciliationService reconciliationService;

    public InventoryReconciliationController(
            InventoryReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/{productId}/reconciliation")
    public ResponseEntity<InventoryReconciliationResponse> reconcile(
            @PathVariable Long productId) {

        return ResponseEntity.ok(
                reconciliationService.reconcile(productId)
        );
    }
}