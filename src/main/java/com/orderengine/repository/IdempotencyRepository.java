
package com.orderengine.repository;

import com.orderengine.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRepository
        extends JpaRepository<IdempotencyRecord, String> {

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records
                (idempotency_key, request_hash, created_at)
            VALUES
                (:key, :requestHash, CURRENT_TIMESTAMP)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int tryReserveKey(
            @Param("key") String key,
            @Param("requestHash") String requestHash
    );
}
