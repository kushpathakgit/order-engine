package com.orderengine.repository;

import com.orderengine.entity.InventoryReservation;
import com.orderengine.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Modifying;

import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;

import java.util.Optional;

@Repository
public interface    ReservationRepository
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

    @Query(value = """
        SELECT *
        FROM inventory_reservations
        WHERE status = 'RESERVED'
          AND expires_at <= :now
        ORDER BY expires_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<InventoryReservation> findExpiredReservationsForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Query("""
        SELECT COALESCE(SUM(r.quantity), 0)
        FROM InventoryReservation r
        WHERE r.productId = :productId
          AND r.status = :status
        """)
    Long sumQuantityByProductIdAndStatus(
            @Param("productId") Long productId,
            @Param("status") ReservationStatus status
    );

    @Query("""
        SELECT COUNT(r)
        FROM InventoryReservation r
        WHERE r.productId = :productId
          AND r.status IN :statuses
        """)
    long countByProductIdAndStatuses(
            @Param("productId") Long productId,
            @Param("statuses") List<ReservationStatus> statuses
    );

    List<InventoryReservation> findByProductIdAndStatus(
            Long productId,
            ReservationStatus status
    );

    @Query("""
        SELECT COUNT(r)
        FROM InventoryReservation r
        WHERE r.productId = :productId
          AND r.status IN :statuses
        """)
    long countTransitionalReservations(
            @Param("productId") Long productId,
            @Param("statuses") List<ReservationStatus> statuses
    );

    @Query("""
        SELECT r
        FROM InventoryReservation r
        WHERE r.status = :status
        ORDER BY r.createdAt ASC
        """)
    List<InventoryReservation> findByStatus(
            @Param("status") ReservationStatus status
    );

    @Query("""
        SELECT r
        FROM InventoryReservation r
        WHERE r.status = :status
          AND r.updatedAt <= :cutoff
        ORDER BY r.updatedAt ASC
        """)
    List<InventoryReservation> findStuckReservations(
            @Param("status") ReservationStatus status,
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    @Query(value = """
        SELECT *
        FROM inventory_reservations
        WHERE status = :status
          AND updated_at <= :cutoff
        ORDER BY updated_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<InventoryReservation> findStuckReservationsForUpdate(
            @Param("status") String status,
            @Param("cutoff") Instant cutoff,
            @Param("batchSize") int batchSize
    );

}