package com.orderengine.dto;

public record ReservationCreationResult(
        ReservationResponse reservation,
        boolean created
) {}