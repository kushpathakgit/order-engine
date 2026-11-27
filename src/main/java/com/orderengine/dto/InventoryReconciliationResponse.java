package com.orderengine.dto;

public record InventoryReconciliationResponse(
        Long productId,
        Integer postgresStock,
        Long activeReservedQuantity,
        Integer expectedRedisStock,
        Integer actualRedisStock,
        Integer difference,
        String status,
        String message
) {}