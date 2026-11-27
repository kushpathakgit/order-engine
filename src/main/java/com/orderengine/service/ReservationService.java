package com.orderengine.service;

import com.orderengine.dto.ReservationCreationResult;
import com.orderengine.dto.ReservationRequest;
import com.orderengine.dto.ReservationResponse;
import com.orderengine.entity.InventoryReservation;
import com.orderengine.exception.IdempotencyKeyReuseException;
import com.orderengine.exception.InvalidIdempotencyKeyException;
import com.orderengine.exception.ResourceNotFoundException;
import com.orderengine.repository.ProductRepository;
import com.orderengine.repository.ReservationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ProductRepository productRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            ProductRepository productRepository) {

        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public ReservationCreationResult createPendingReservation(
            ReservationRequest request,
            String idempotencyKey) {

        validateIdempotencyKey(idempotencyKey);

        boolean productExists =
                productRepository.existsById(request.productId());

        if (!productExists) {
            throw new ResourceNotFoundException(
                    "Product not found with id: " + request.productId());
        }

        String requestHash = generateRequestHash(request);

        String reservationId = UUID.randomUUID().toString();

        int inserted = reservationRepository.tryCreateReservation(
                reservationId,
                request.productId(),
                request.quantity(),
                idempotencyKey,
                requestHash
        );

        if (inserted == 1) {

            InventoryReservation reservation =
                    reservationRepository.findById(reservationId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Created reservation could not be found"));

            return new ReservationCreationResult(
                    ReservationResponse.from(reservation),
                    true
            );
        }

        InventoryReservation existing =
                reservationRepository.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException(
                                "Conflicting reservation could not be found"));

        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException(
                    "Idempotency key was already used with a different request");
        }

        return new ReservationCreationResult(
                ReservationResponse.from(existing),
                false
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