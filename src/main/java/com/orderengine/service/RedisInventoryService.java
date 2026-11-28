
package com.orderengine.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.orderengine.entity.InventoryReservation;
import com.orderengine.entity.ReservationStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RedisInventoryService {


    private static final DefaultRedisScript<Long> RECOVERY_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local postgresStock = tonumber(ARGV[1])
    
                    if postgresStock == nil or postgresStock < 0 then
                        return -1
                    end
    
                    if #KEYS ~= #ARGV then
                        return -3
                    end
    
                    local reservedTotal = 0
    
                    for i = 2, #ARGV do
                        local quantity = tonumber(ARGV[i])
    
                        if quantity == nil or quantity <= 0 then
                            return -1
                        end
    
                        reservedTotal = reservedTotal + quantity
                    end
    
                    if reservedTotal > postgresStock then
                        return -2
                    end
    
                    local availableStock = postgresStock - reservedTotal
    
                    redis.call(
                        'SET',
                        KEYS[1],
                        tostring(availableStock)
                    )
    
                    for i = 2, #KEYS do
                        redis.call(
                            'SET',
                            KEYS[i],
                            ARGV[i]
                        )
                    end
    
                    return 1
                    """,
                    Long.class
            );


    private static final DefaultRedisScript<Long> IDEMPOTENT_RESERVE_SCRIPT;

    static {
        IDEMPOTENT_RESERVE_SCRIPT = new DefaultRedisScript<>();

        IDEMPOTENT_RESERVE_SCRIPT.setScriptText("""
            -- Check whether this reservation was processed before
            local existing = redis.call('GET', KEYS[2])

            if existing then
                if tonumber(existing) == tonumber(ARGV[1]) then
                    return 2
                else
                    return -2
                end
            end

            -- Read current inventory
            local stock = redis.call('GET', KEYS[1])

            if not stock then
                return -1
            end

            local quantity = tonumber(ARGV[1])
            local available = tonumber(stock)

            if available < quantity then
                return 0
            end

            -- Deduct inventory and record reservation together
            redis.call('DECRBY', KEYS[1], quantity)
            redis.call('SET', KEYS[2], quantity)

            return 1
            """);

        IDEMPOTENT_RESERVE_SCRIPT.setResultType(Long.class);
    }

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT;

    static {
        RELEASE_SCRIPT = new DefaultRedisScript<>();

        RELEASE_SCRIPT.setScriptText("""
            -- Find the reservation marker
            local marker = redis.call('GET', KEYS[2])

            if not marker then
                return -1
            end

            local quantity = tonumber(ARGV[1])
            local reservedQuantity = tonumber(marker)

            -- Already released: do not increment stock again
            if not reservedQuantity then
                if marker == 'RELEASED:' .. ARGV[1] then
                    return 2
                end

                return -2
            end

            -- Ensure this is the expected reservation
            if reservedQuantity ~= quantity then
                return -2
            end

            -- Inventory key must exist
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -3
            end

            -- Return inventory and mark reservation released
            redis.call('INCRBY', KEYS[1], quantity)
            redis.call('SET', KEYS[2], 'RELEASED:' .. ARGV[1])

            return 1
            """);

        RELEASE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT;

    static {
        RESERVE_SCRIPT = new DefaultRedisScript<>();

        RESERVE_SCRIPT.setScriptText("""
                local stock = redis.call('GET', KEYS[1])

                if not stock then
                    return -1
                end

                local quantity = tonumber(ARGV[1])
                local available = tonumber(stock)

                if available < quantity then
                    return 0
                end

                redis.call('DECRBY', KEYS[1], quantity)

                return 1
                """);

        RESERVE_SCRIPT.setResultType(Long.class);
    }

    public RedisInventoryService(
            StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getInventoryKey(Long productId) {
        return "order-engine:inventory:product:" + productId;
    }

    public boolean initializeStock(Long productId, int stock) {

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException(
                    "Product ID must be positive");
        }

        if (stock < 0) {
            throw new IllegalArgumentException(
                    "Stock cannot be negative");
        }

        Boolean initialized = redisTemplate.opsForValue()
                .setIfAbsent(
                        getInventoryKey(productId),
                        String.valueOf(stock)
                );

        return Boolean.TRUE.equals(initialized);
    }

    public Long reserveStock(Long productId, int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero");
        }

        String key = getInventoryKey(productId);

        return redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(key),
                String.valueOf(quantity)
        );
    }

    public Integer getAvailableStock(Long productId) {

        String value = redisTemplate.opsForValue()
                .get(getInventoryKey(productId));

        return value == null ? null : Integer.valueOf(value);
    }

    public Long reserveStockIdempotently(
            Long productId,
            int quantity,
            String reservationId) {

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException(
                    "Product ID must be positive");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero");
        }

        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Reservation ID is required");
        }

        String inventoryKey = getInventoryKey(productId);

        String reservationKey =
                "order-engine:reservation:" + reservationId;

        return redisTemplate.execute(
                IDEMPOTENT_RESERVE_SCRIPT,
                List.of(inventoryKey, reservationKey),
                String.valueOf(quantity)
        );
    }
    public Long releaseStock(
            Long productId,
            int quantity,
            String reservationId) {

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException(
                    "Product ID must be positive");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero");
        }

        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Reservation ID is required");
        }

        String inventoryKey = getInventoryKey(productId);

        String reservationKey =
                "order-engine:reservation:" + reservationId;

        return redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(inventoryKey, reservationKey),
                String.valueOf(quantity)
        );
    }

    public Long rebuildInventory(
            Long productId,
            int postgresStock,
            List<InventoryReservation> activeReservations) {

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Invalid product ID");
        }

        if (postgresStock < 0) {
            throw new IllegalArgumentException(
                    "PostgreSQL stock cannot be negative");
        }

        List<String> keys = new ArrayList<>();
        List<String> arguments = new ArrayList<>();

        keys.add(getInventoryKey(productId));
        arguments.add(String.valueOf(postgresStock));

        for (InventoryReservation reservation : activeReservations) {

            if (!reservation.getProductId().equals(productId)
                    || reservation.getStatus() != ReservationStatus.RESERVED) {
                throw new IllegalArgumentException(
                        "Invalid active reservation supplied for recovery");
            }

            keys.add(
                    "order-engine:reservation:"
                            + reservation.getReservationId()
            );

            arguments.add(String.valueOf(reservation.getQuantity()));
        }

        return redisTemplate.execute(
                RECOVERY_SCRIPT,
                keys,
                arguments.toArray()
        );
    }

}
