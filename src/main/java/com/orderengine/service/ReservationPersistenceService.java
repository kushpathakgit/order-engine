package com.orderengine.service;

import com.orderengine.entity.InventoryReservation;
import com.orderengine.entity.ReservationStatus;
import com.orderengine.exception.IdempotencyKeyReuseException;
import com.orderengine.exception.ResourceNotFoundException;
import com.orderengine.repository.ProductRepository;
import com.orderengine.repository.ReservationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReservationPersistenceService {

    private final ReservationRepository reservationRepository;
    private final ProductRepository productRepository;

    public ReservationPersistenceService(
            ReservationRepository reservationRepository,
            ProductRepository productRepository) {
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public ReservationCreationData createOrGet(
            Long productId,
            Integer quantity,
            String idempotencyKey,
            String requestHash) {

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException(
                    "Product not found with id: " + productId);
        }

        String reservationId = UUID.randomUUID().toString();

        int inserted = reservationRepository.tryCreateReservation(
                reservationId,
                productId,
                quantity,
                idempotencyKey,
                requestHash
        );

        InventoryReservation reservation;

        if (inserted == 1) {
            reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Created reservation could not be found"));
        } else {
            reservation = reservationRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Conflicting reservation could not be found"));

            if (!reservation.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReuseException(
                        "Idempotency key was already used with a different request");
            }
        }

        return new ReservationCreationData(reservation, inserted == 1);
    }

    @Transactional
    public InventoryReservation markReserved(String reservationId) {

        InventoryReservation reservation =
                reservationRepository.findByIdForUpdate(reservationId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Reservation not found: " + reservationId));

        if (reservation.getStatus() == ReservationStatus.PENDING) {
            reservation.markReserved();
        } else if (reservation.getStatus() != ReservationStatus.RESERVED
                && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Cannot reserve inventory for reservation in state: "
                            + reservation.getStatus());
        }

        return reservation;
    }

    @Transactional
    public InventoryReservation markFailed(String reservationId) {

        InventoryReservation reservation =
                reservationRepository.findByIdForUpdate(reservationId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Reservation not found: " + reservationId));

        // Only a still-pending reservation can be marked failed.
        if (reservation.getStatus() == ReservationStatus.PENDING) {
            reservation.fail();
        }

        return reservation;
    }

    public record ReservationCreationData(
            InventoryReservation reservation,
            boolean created
    ) {}
}