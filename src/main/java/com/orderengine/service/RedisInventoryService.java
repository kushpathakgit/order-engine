
package com.orderengine.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RedisInventoryService {

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

    public void initializeStock(Long productId, int stock) {

        if (stock < 0) {
            throw new IllegalArgumentException(
                    "Stock cannot be negative");
        }

        redisTemplate.opsForValue().set(
                getInventoryKey(productId),
                String.valueOf(stock)
        );
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
}
