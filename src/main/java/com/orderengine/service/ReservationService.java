package com.orderengine.service;

import com.orderengine.dto.ReservationCreationResult;
import com.orderengine.dto.ReservationRequest;
import com.orderengine.dto.ReservationResponse;
import com.orderengine.entity.InventoryReservation;
import com.orderengine.entity.ReservationStatus;
import com.orderengine.exception.InvalidIdempotencyKeyException;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ReservationService {

    private final ReservationPersistenceService persistenceService;
    private final RedisInventoryService inventoryService;

    public ReservationService(
            ReservationPersistenceService persistenceService,
            RedisInventoryService inventoryService) {
        this.persistenceService = persistenceService;
        this.inventoryService = inventoryService;
    }

    public ReservationCreationResult createReservation(
            ReservationRequest request,
            String idempotencyKey) {

        validateIdempotencyKey(idempotencyKey);

        String requestHash = generateRequestHash(request);

        // Step 1: Create or retrieve the durable PostgreSQL record.
        var data = persistenceService.createOrGet(
                request.productId(),
                request.quantity(),
                idempotencyKey,
                requestHash
        );

        InventoryReservation reservation = data.reservation();

        // Step 2: Return terminal or already-reserved states without
        // attempting another Redis deduction.
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            return new ReservationCreationResult(
                    ReservationResponse.from(reservation),
                    data.created()
            );
        }

        // Step 3: Ask Redis to reserve inventory idempotently.
        Long result;

        try {
            result = inventoryService.reserveStockIdempotently(
                    reservation.getProductId(),
                    reservation.getQuantity(),
                    reservation.getReservationId()
            );
        } catch (DataAccessException ex) {
            // Keep PENDING. A retry can safely use the same reservation ID.
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Inventory service is temporarily unavailable",
                    ex
            );
        }

        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Inventory service returned no result"
            );
        }

        // Step 4: Handle insufficient inventory.
        if (result == 0) {
            InventoryReservation failed =
                    persistenceService.markFailed(
                            reservation.getReservationId());

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Insufficient inventory. Reservation status: "
                            + failed.getStatus()
            );
        }

        // Inventory is not initialized. Leave PENDING for recovery/retry.
        if (result == -1) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Inventory has not been initialized in Redis"
            );
        }

        if (result == -2) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reservation ID was reused with a different quantity"
            );
        }

        if (result != 1 && result != 2) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected inventory reservation result"
            );
        }

        // Step 5: Redis reserved stock (or confirmed an earlier retry).
        // Persist the RESERVED state in a separate short DB transaction.
        InventoryReservation reserved =
                persistenceService.markReserved(
                        reservation.getReservationId());

        return new ReservationCreationResult(
                ReservationResponse.from(reserved),
                data.created()
        );
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency key must contain between 1 and 100 characters");
        }
    }

    private String generateRequestHash(ReservationRequest request) {

        String value = request.productId() + ":" + request.quantity();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", ex);
        }
    }
}