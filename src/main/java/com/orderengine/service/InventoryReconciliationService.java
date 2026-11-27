package com.orderengine.service;

import com.orderengine.dto.InventoryReconciliationResponse;
import com.orderengine.entity.InventoryReservation;
import com.orderengine.entity.ReservationStatus;
import com.orderengine.entity.Product;
import com.orderengine.exception.ResourceNotFoundException;
import org.springframework.dao.DataAccessException;
import com.orderengine.repository.ProductRepository;
import com.orderengine.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryReconciliationService {

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final RedisInventoryService inventoryService;

    private static final List<ReservationStatus> TRANSITIONAL_STATUSES =
            List.of(
                    ReservationStatus.PENDING,
                    ReservationStatus.RELEASING,
                    ReservationStatus.EXPIRING
            );
    private long expectedRedisStock;

    public InventoryReconciliationService(
            ProductRepository productRepository,
            ReservationRepository reservationRepository,
            RedisInventoryService inventoryService) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryService = inventoryService;
    }

    public InventoryReconciliationResponse reconcile(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found: " + productId));

        long activeReservedQuantity =
                reservationRepository.sumQuantityByProductIdAndStatus(
                        productId,
                        ReservationStatus.RESERVED
                );

        long transitionalCount =
                reservationRepository.countByProductIdAndStatuses(
                        productId,
                        TRANSITIONAL_STATUSES
                );

        Integer actualRedisStock;

        try {
            actualRedisStock = inventoryService.getAvailableStock(productId);
        } catch (DataAccessException ex) {
            return new InventoryReconciliationResponse(
                    productId,
                    product.getAvailableStock(),
                    activeReservedQuantity,
                    Math.toIntExact(expectedRedisStock),
                    null,
                    null,
                    "REDIS_UNAVAILABLE",
                    "Could not connect to Redis. Reconciliation was not completed."
            );
        }

        long expectedRedisStock =
                product.getAvailableStock() - activeReservedQuantity;

        if (transitionalCount > 0) {
            return new InventoryReconciliationResponse(
                    productId,
                    product.getAvailableStock(),
                    activeReservedQuantity,
                    Math.toIntExact(expectedRedisStock),
                    actualRedisStock,
                    null,
                    "INDETERMINATE",
                    "Transitional reservations exist. Retry reconciliation after they complete."
            );
        }

        if (actualRedisStock == null) {
            return new InventoryReconciliationResponse(
                    productId,
                    product.getAvailableStock(),
                    activeReservedQuantity,
                    Math.toIntExact(expectedRedisStock),
                    null,
                    null,
                    "REDIS_MISSING",
                    "Redis inventory key is missing."
            );
        }

        if (expectedRedisStock < 0) {
            return new InventoryReconciliationResponse(
                    productId,
                    product.getAvailableStock(),
                    activeReservedQuantity,
                    Math.toIntExact(expectedRedisStock),
                    actualRedisStock,
                    null,
                    "DATABASE_INCONSISTENT",
                    "Active reservations exceed PostgreSQL available stock."
            );
        }

        long difference = expectedRedisStock - actualRedisStock;

        if (difference == 0) {
            return new InventoryReconciliationResponse(
                    productId,
                    product.getAvailableStock(),
                    activeReservedQuantity,
                    Math.toIntExact(expectedRedisStock),
                    actualRedisStock,
                    0,
                    "MATCH",
                    "PostgreSQL and Redis inventory are consistent."
            );
        }

        return new InventoryReconciliationResponse(
                productId,
                product.getAvailableStock(),
                activeReservedQuantity,
                Math.toIntExact(expectedRedisStock),
                actualRedisStock,
                Math.toIntExact(difference),
                "MISMATCH",
                "Redis inventory differs from the expected inventory."
        );
    }
}