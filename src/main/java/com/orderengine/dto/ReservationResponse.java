package com.orderengine.dto;

import com.orderengine.entity.InventoryReservation;
import com.orderengine.entity.ReservationStatus;

import java.time.Instant;

public record ReservationResponse(
        String reservationId,
        Long productId,
        Integer quantity,
        ReservationStatus status,
        Instant expiresAt,
        Long orderId
) {

    public static ReservationResponse from(
            InventoryReservation reservation) {

        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getProductId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getOrderId()
        );
    }
}