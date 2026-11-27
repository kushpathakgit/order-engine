
package com.orderengine.controller;

import com.orderengine.service.RedisInventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/redis/inventory")
public class RedisInventoryTestController {

    private final RedisInventoryService inventoryService;

    public RedisInventoryTestController(
            RedisInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/{productId}/initialize")
    public ResponseEntity<String> initialize(
            @PathVariable Long productId,
            @RequestParam int stock) {

        inventoryService.initializeStock(productId, stock);

        return ResponseEntity.ok(
                "Redis inventory initialized with stock: " + stock);
    }

    @PostMapping("/{productId}/reserve")
    public ResponseEntity<String> reserve(
            @PathVariable Long productId,
            @RequestParam int quantity) {

        Long result = inventoryService.reserveStock(
                productId, quantity);

        if (result == null) {
            return ResponseEntity.internalServerError()
                    .body("Redis returned no reservation result");
        }

        if (result == 1) {
            return ResponseEntity.ok("Reservation accepted");
        }

        if (result == 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Insufficient stock");
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Inventory not initialized");
    }

    @GetMapping("/{productId}")
    public ResponseEntity<String> getStock(
            @PathVariable Long productId) {

        Integer stock = inventoryService.getAvailableStock(productId);

        if (stock == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                "Available Redis stock: " + stock);
    }

    @PostMapping("/{productId}/reserve/{reservationId}")
    public ResponseEntity<String> reserveIdempotently(
            @PathVariable Long productId,
            @PathVariable String reservationId,
            @RequestParam int quantity) {

        Long result = inventoryService.reserveStockIdempotently(
                productId,
                quantity,
                reservationId
        );

        if (result == null) {
            return ResponseEntity.internalServerError()
                    .body("Redis returned no reservation result");
        }

        if (result == 1) {
            return ResponseEntity.ok(
                    "Inventory reserved successfully");
        }

        if (result == 2) {
            return ResponseEntity.ok(
                    "Reservation already exists. No additional stock deducted.");
        }

        if (result == 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Insufficient stock");
        }

        if (result == -1) {
            return ResponseEntity.notFound()
                    .build();
        }

        if (result == -2) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Reservation ID reused with a different quantity");
        }

        return ResponseEntity.internalServerError()
                .body("Unexpected Redis reservation result");
    }
}
