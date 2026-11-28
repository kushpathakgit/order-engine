package com.orderengine.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_reservations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reservation_idempotency_key",
                        columnNames = "idempotency_key"
                ),
                @UniqueConstraint(
                        name = "uk_reservation_order_id",
                        columnNames = "order_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_reservation_status_expiry",
                        columnList = "status, expires_at"
                ),
                @Index(
                        name = "idx_reservation_product",
                        columnList = "product_id"
                )
        }
)
public class InventoryReservation {


    @Column(name = "recovery_token")
    private UUID recoveryToken;

    @Column(name = "recovery_locked_until")
    private Instant recoveryLockedUntil;

    @Column(name = "recovery_attempts", nullable = false)
    private int recoveryAttempts = 0;

    @Column(name = "next_recovery_at")
    private Instant nextRecoveryAt;

    @Column(name = "last_recovery_error", columnDefinition = "TEXT")
    private String lastRecoveryError;

    @Id
    @Column(name = "reservation_id", length = 36, nullable = false)
    private String reservationId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "order_id", unique = true)
    private Long orderId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected InventoryReservation() {
        // Required by JPA
    }

    public InventoryReservation(
            String reservationId,
            Long productId,
            Integer quantity,
            String idempotencyKey,
            String requestHash,
            Instant expiresAt) {

        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Reservation ID is required");
        }

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException(
                    "Product ID must be positive");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be positive");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required");
        }

        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Request hash is required");
        }

        if (expiresAt == null) {
            throw new IllegalArgumentException(
                    "Expiry time is required");
        }

        this.reservationId = reservationId;
        this.productId = productId;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.expiresAt = expiresAt;
        this.status = ReservationStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markReserved() {
        requireStatus(ReservationStatus.PENDING);
        this.status = ReservationStatus.RESERVED;
    }

    public void confirm(Long orderId) {
        requireStatus(ReservationStatus.RESERVED);

        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException(
                    "Valid order ID is required");
        }

        this.orderId = orderId;
        this.status = ReservationStatus.CONFIRMED;
    }

    public void markReleasing() {
        requireStatus(ReservationStatus.RESERVED);
        this.status = ReservationStatus.RELEASING;
    }

    public void markReleased() {
        requireStatus(ReservationStatus.RELEASING);
        this.status = ReservationStatus.RELEASED;
    }

    public void markExpiring() {
        requireStatus(ReservationStatus.RESERVED);
        this.status = ReservationStatus.EXPIRING;
    }

    public void markExpired() {
        requireStatus(ReservationStatus.EXPIRING);
        this.status = ReservationStatus.EXPIRED;
    }


    public void fail() {
        requireStatus(ReservationStatus.PENDING);
        this.status = ReservationStatus.FAILED;
    }

    private void requireStatus(ReservationStatus expectedStatus) {
        if (this.status != expectedStatus) {
            throw new IllegalStateException(
                    "Cannot perform this transition from status: "
                            + this.status);
        }
    }

    public String getReservationId() {
        return reservationId;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void claimRecovery(UUID token, Instant lockedUntil) {
        this.recoveryToken = token;
        this.recoveryLockedUntil = lockedUntil;
        this.recoveryAttempts++;
    }

    public void scheduleRecoveryRetry(
            UUID token,
            String error,
            Instant retryAt
    ) {
        requireRecoveryToken(token);

        this.lastRecoveryError = error;
        this.nextRecoveryAt = retryAt;

        this.recoveryToken = null;
        this.recoveryLockedUntil = null;
    }

    public void completeRecovery(UUID token) {
        requireRecoveryToken(token);

        this.recoveryToken = null;
        this.recoveryLockedUntil = null;
        this.nextRecoveryAt = null;
        this.lastRecoveryError = null;
    }

    public String getLastRecoveryError() {
        return lastRecoveryError;
    }

    public Instant getNextRecoveryAt() {
        return nextRecoveryAt;
    }

    public int getRecoveryAttempts() {
        return recoveryAttempts;
    }

    public Instant getRecoveryLockedUntil() {
        return recoveryLockedUntil;
    }

    public UUID getRecoveryToken() {
        return recoveryToken;
    }

    public void requireRecoveryToken(UUID token) {
        if (recoveryToken == null || !recoveryToken.equals(token)) {
            throw new IllegalStateException(
                    "Recovery lease is no longer owned by this worker"
            );
        }
    }

}