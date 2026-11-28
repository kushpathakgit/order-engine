package com.orderengine.service;

import com.orderengine.entity.ReservationStatus;

import java.util.UUID;

public record RecoveryTask(
        UUID reservationId,
        Long productId,
        Integer quantity,
        ReservationStatus status,
        UUID recoveryToken,
        int recoveryAttempts
) {}