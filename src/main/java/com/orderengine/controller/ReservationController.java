package com.orderengine.controller;

import com.orderengine.dto.ReservationCreationResult;
import com.orderengine.dto.ReservationRequest;
import com.orderengine.service.ReservationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(
            ReservationService reservationService) {
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
}