package com.orderengine.controller;

import com.orderengine.dto.OrderResponseDTO;
import com.orderengine.dto.ReservationCreationResult;
import com.orderengine.dto.ReservationRequest;
import com.orderengine.dto.ReservationResponse;
import com.orderengine.service.ReservationConfirmationService;
import com.orderengine.service.ReservationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationConfirmationService confirmationService;
    private final ReservationService reservationService;

    public ReservationController(
            ReservationConfirmationService confirmationService, ReservationService reservationService) {
        this.confirmationService = confirmationService;
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<?> createReservation(
            @Valid @RequestBody ReservationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        ReservationCreationResult result =
                reservationService.createReservation(
                        request, idempotencyKey);

        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(result.reservation());
        }

        return ResponseEntity.ok(result.reservation());
    }

    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> releaseReservation(
            @PathVariable String reservationId) {

        ReservationResponse response =
                reservationService.releaseReservation(reservationId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<OrderResponseDTO> confirmReservation(
            @PathVariable String reservationId) {

        OrderResponseDTO response =
                confirmationService.confirmReservation(reservationId);

        return ResponseEntity.ok(response);
    }
}