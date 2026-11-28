
package com.orderengine.worker;

import com.orderengine.entity.ReservationStatus;
import com.orderengine.service.RecoveryTask;
import com.orderengine.service.RedisInventoryService;
import com.orderengine.service.ReservationPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ReservationRecoveryWorker {

    private static final Logger log =
            LoggerFactory.getLogger(ReservationRecoveryWorker.class);

    private final ReservationPersistenceService persistenceService;
    private final RedisInventoryService redisInventoryService;

    @Value("${reservation.recovery.stuck-after-seconds:30}")
    private long stuckAfterSeconds;

    @Value("${reservation.recovery.batch-size:50}")
    private int batchSize;

    public ReservationRecoveryWorker(
            ReservationPersistenceService persistenceService,
            RedisInventoryService redisInventoryService
    ) {
        this.persistenceService = persistenceService;
        this.redisInventoryService = redisInventoryService;
    }

    @Scheduled(
            fixedDelayString =
                    "${reservation.recovery.worker-delay-ms:10000}"
    )
    public void recoverStuckReservations() {

        Instant cutoff = Instant.now()
                .minusSeconds(stuckAfterSeconds);

        List<RecoveryTask> tasks =
                persistenceService.claimStuckReservations(
                        cutoff,
                        batchSize
                );

        for (RecoveryTask task : tasks) {
            processTask(task);
        }
    }

    private void processTask(RecoveryTask task) {

        try {
            ReservationStatus status = task.status();

            if (status == ReservationStatus.PENDING) {
                recoverPendingReservation(task);
            } else if (status == ReservationStatus.RELEASING) {
                recoverRelease(task);
            } else if (status == ReservationStatus.EXPIRING) {
                recoverExpiry(task);
            }

        } catch (Exception exception) {

            log.error(
                    "Recovery failed for reservation {}",
                    task.reservationId(),
                    exception
            );

            scheduleRetry(task, exception);
        }
    }

    private void recoverPendingReservation(RecoveryTask task) {

        Long result =
                redisInventoryService.reserveStockIdempotently(
                        task.productId(),
                        task.quantity(),
                        task.reservationId()
                );

        if (result != null && (result == 1L || result == 2L)) {

            persistenceService.completePendingRecovery(
                    task.reservationId(),
                    task.recoveryToken()
            );

            log.info(
                    "Pending reservation recovered: {}",
                    task.reservationId()
            );

            return;
        }

        if (result != null && result == 0L) {
            persistenceService.failPendingRecovery(
                    task.reservationId(),
                    task.recoveryToken()
            );

            return;
        }

        throw new IllegalStateException(
                "Redis reservation returned unexpected result: " + result
        );
    }

    private void recoverRelease(RecoveryTask task) {

        Long result = redisInventoryService.releaseStock(
                task.productId(),
                task.quantity(),
                task.reservationId()
        );

        if (result != null && (result == 1L || result == 2L)) {

            persistenceService.completeReleaseRecovery(
                    task.reservationId(),
                    task.recoveryToken()
            );

            log.info(
                    "Release recovered: {}",
                    task.reservationId()
            );

            return;
        }

        throw new IllegalStateException(
                "Redis release returned unexpected result: " + result
        );
    }

    private void recoverExpiry(RecoveryTask task) {

        Long result = redisInventoryService.releaseStock(
                task.productId(),
                task.quantity(),
                task.reservationId()
        );

        if (result != null && (result == 1L || result == 2L)) {

            persistenceService.completeExpiryRecovery(
                    task.reservationId(),
                    task.recoveryToken()
            );

            log.info(
                    "Expiry recovered: {}",
                    task.reservationId()
            );

            return;
        }

        throw new IllegalStateException(
                "Redis expiry release returned unexpected result: " + result
        );
    }

    private void scheduleRetry(
            RecoveryTask task,
            Exception exception
    ) {
        try {
            persistenceService.scheduleRecoveryFailure(
                    task.reservationId(),
                    task.recoveryToken(),
                    exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage()
            );
        } catch (Exception leaseException) {
            log.warn(
                    "Could not schedule retry for reservation {}. "
                            + "The lease may have expired or been claimed by "
                            + "another worker.",
                    task.reservationId(),
                    leaseException
            );
        }
    }
}
