
package com.orderengine.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String tryAcquire(String lockKey, Duration lockDuration) {

        String token = UUID.randomUUID().toString();

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, lockDuration);

            return Boolean.TRUE.equals(acquired) ? token : null;

        } catch (DataAccessException ex) {
            // Redis unavailable: caller must use its fallback strategy.
            return null;
        }
    }

    public void release(String lockKey, String token) {

        if (token == null) {
            return;
        }

        try {
            String script = """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    else
                        return 0
                    end
                    """;

            redisTemplate.execute(
                    new org.springframework.data.redis.core.script
                            .DefaultRedisScript<>(script, Long.class),
                    java.util.List.of(lockKey),
                    token
            );

        } catch (DataAccessException ex) {
            // Lock will eventually expire automatically.
        }
    }
}
