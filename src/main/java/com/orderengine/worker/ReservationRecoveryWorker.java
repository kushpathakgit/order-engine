package com.orderengine.worker;

import com.orderengine.entity.ReservationStatus;
import com.orderengine.service.RedisInventoryService;
import com.orderengine.service.ReservationPersistenceService;
import com.orderengine.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ReservationRecoveryWorker {

    private static final Logger log =
            LoggerFactory.getLogger(ReservationRecoveryWorker.class);

    private static final int BATCH_SIZE = 50;

    private final ReservationPersistenceService persistenceService;
    private final ReservationService reservationService;
    private final RedisInventoryService inventoryService;

    public ReservationRecoveryWorker(
            ReservationPersistenceService persistenceService,
            ReservationService reservationService,
            RedisInventoryService inventoryService) {

        this.persistenceService = persistenceService;
        this.reservationService = reservationService;
        this.inventoryService = inventoryService;
    }

    @Scheduled(fixedDelayString =
            "${reservation.recovery.worker-delay-ms:10000}")
    public void recoverStuckReservations() {

        Instant cutoff = Instant.now().minusSeconds(30);

        recoverPending(cutoff);
        recoverReleasing(cutoff);
        recoverExpiring(cutoff);
    }

    private void recoverPending(Instant cutoff) {

        List<ReservationPersistenceService.RecoveryTask> tasks =
                persistenceService.claimStuckReservations(
                        ReservationStatus.PENDING,
                        cutoff,
                        BATCH_SIZE
                );

        for (var task : tasks) {
            try {
                reservationService.retryPendingReservation(
                        task.reservationId(),
                        task.productId(),
                        task.quantity()
                );
            } catch (Exception ex) {
                log.error(
                        "Could not recover pending reservation {}",
                        task.reservationId(),
                        ex
                );
            }
        }
    }

    private void recoverReleasing(Instant cutoff) {

        List<ReservationPersistenceService.RecoveryTask> tasks =
                persistenceService.claimStuckReservations(
                        ReservationStatus.RELEASING,
                        cutoff,
                        BATCH_SIZE
                );

        for (var task : tasks) {
            recoverRelease(task, false);
        }
    }

    private void recoverExpiring(Instant cutoff) {

        List<ReservationPersistenceService.RecoveryTask> tasks =
                persistenceService.claimStuckReservations(
                        ReservationStatus.EXPIRING,
                        cutoff,
                        BATCH_SIZE
                );

        for (var task : tasks) {
            recoverRelease(task, true);
        }
    }

    private void recoverRelease(
            ReservationPersistenceService.RecoveryTask task,
            boolean expiry) {

        try {
            Long result = inventoryService.releaseStock(
                    task.productId(),
                    task.quantity(),
                    task.reservationId()
            );

            if (result == null || (result != 1 && result != 2)) {
                log.warn(
                        "Redis release recovery failed for {}. Result: {}",
                        task.reservationId(),
                        result
                );
                return;
            }

            if (expiry) {
                persistenceService.completeExpiry(task.reservationId());
            } else {
                persistenceService.completeRelease(task.reservationId());
            }

            log.info("Recovered reservation {}", task.reservationId());

        } catch (Exception ex) {
            log.error(
                    "Could not recover reservation {}",
                    task.reservationId(),
                    ex
            );
        }
    }
}