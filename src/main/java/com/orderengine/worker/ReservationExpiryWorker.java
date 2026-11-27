package com.orderengine.worker;

import com.orderengine.service.RedisInventoryService;
import com.orderengine.service.ReservationPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReservationExpiryWorker {

    private static final Logger log =
            LoggerFactory.getLogger(ReservationExpiryWorker.class);

    private final ReservationPersistenceService persistenceService;
    private final RedisInventoryService inventoryService;

    public ReservationExpiryWorker(
            ReservationPersistenceService persistenceService,
            RedisInventoryService inventoryService) {
        this.persistenceService = persistenceService;
        this.inventoryService = inventoryService;
    }

    @Scheduled(fixedDelayString =
            "${reservation.expiry.worker-delay-ms:5000}")
    public void processExpiredReservations() {

        List<ReservationPersistenceService.ExpiryTask> tasks =
                persistenceService.claimExpiredReservations(50);

        for (ReservationPersistenceService.ExpiryTask task : tasks) {
            processSingleReservation(task);
        }
    }

    private void processSingleReservation(
            ReservationPersistenceService.ExpiryTask task) {

        try {
            Long result = inventoryService.releaseStock(
                    task.productId(),
                    task.quantity(),
                    task.reservationId()
            );

            if (result == null) {
                log.warn("Redis returned no result for reservation {}",
                        task.reservationId());
                return;
            }

            if (result == 1 || result == 2) {
                persistenceService.completeExpiry(task.reservationId());

                log.info("Reservation {} expired successfully",
                        task.reservationId());
                return;
            }

            log.warn(
                    "Could not expire reservation {}. Redis result: {}",
                    task.reservationId(),
                    result
            );

        } catch (DataAccessException ex) {
            log.error(
                    "Redis failure while expiring reservation {}",
                    task.reservationId(),
                    ex
            );
        } catch (Exception ex) {
            log.error(
                    "Unexpected error while expiring reservation {}",
                    task.reservationId(),
                    ex
            );
        }
    }
}