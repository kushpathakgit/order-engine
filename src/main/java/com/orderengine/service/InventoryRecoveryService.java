package com.orderengine.service;

import com.orderengine.entity.InventoryReservation;
import com.orderengine.entity.ReservationStatus;
import com.orderengine.entity.Product;
import com.orderengine.exception.ResourceNotFoundException;
import com.orderengine.repository.ProductRepository;
import com.orderengine.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryRecoveryService {

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final RedisInventoryService inventoryService;

    private static final List<ReservationStatus> TRANSITIONAL_STATUSES =
            List.of(
                    ReservationStatus.PENDING,
                    ReservationStatus.RELEASING,
                    ReservationStatus.EXPIRING
            );

    public InventoryRecoveryService(
            ProductRepository productRepository,
            ReservationRepository reservationRepository,
            RedisInventoryService inventoryService) {

        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public void recoverProductInventory(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found: " + productId));

        long transitionalCount =
                reservationRepository.countByProductIdAndStatuses(
                        productId,
                        TRANSITIONAL_STATUSES
                );

        if (transitionalCount > 0) {
            throw new IllegalStateException(
                    "Cannot recover inventory while transitional "
                            + "reservations exist");
        }

        List<InventoryReservation> activeReservations =
                reservationRepository.findByProductIdAndStatus(
                        productId,
                        ReservationStatus.RESERVED
                );

        long reservedQuantity = activeReservations.stream()
                .mapToLong(InventoryReservation::getQuantity)
                .sum();

        if (reservedQuantity > product.getAvailableStock()) {
            throw new IllegalStateException(
                    "Active reservations exceed PostgreSQL stock");
        }

        Long result = inventoryService.rebuildInventory(
                productId,
                product.getAvailableStock(),
                activeReservations
        );

        if (result == null || result != 1) {
            throw new IllegalStateException(
                    "Redis inventory recovery failed. Result: " + result);
        }
    }
}