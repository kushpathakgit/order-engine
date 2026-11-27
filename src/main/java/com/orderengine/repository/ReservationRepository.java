package com.orderengine.repository;

import com.orderengine.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Modifying;


import java.util.Optional;

@Repository
public interface ReservationRepository
        extends JpaRepository<InventoryReservation, String> {

    Optional<InventoryReservation> findByIdempotencyKey(
            String idempotencyKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        INSERT INTO inventory_reservations (
            reservation_id,
            product_id,
            quantity,
            idempotency_key,
            request_hash,
            status,
            expires_at,
            created_at,
            updated_at,
            version
        )
        VALUES (
            :reservationId,
            :productId,
            :quantity,
            :idempotencyKey,
            :requestHash,
            'PENDING',
            CURRENT_TIMESTAMP + INTERVAL '2 minutes',
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            0
        )
        ON CONFLICT (idempotency_key) DO NOTHING
        """, nativeQuery = true)
    int tryCreateReservation(
            @Param("reservationId") String reservationId,
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM InventoryReservation r
        WHERE r.reservationId = :reservationId
        """)
    Optional<InventoryReservation> findByIdForUpdate(
            @Param("reservationId") String reservationId);
}