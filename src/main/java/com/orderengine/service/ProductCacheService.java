
package com.orderengine.service;

import com.orderengine.dto.ProductResponseDTO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;

@Service
public class ProductCacheService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductCacheService.class);

    private static final String KEY_PREFIX =
            "order-engine:product:";

    private static final Duration CACHE_TTL =
            Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {

        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ProductResponseDTO> get(Long productId) {

        String key = KEY_PREFIX + productId;

        try {
            String cachedValue =
                    redisTemplate.opsForValue().get(key);

            if (cachedValue == null) {
                log.info("Product cache MISS for id={}", productId);
                return Optional.empty();
            }

            ProductResponseDTO response =
                    objectMapper.readValue(
                            cachedValue,
                            ProductResponseDTO.class
                    );

            log.info("Product cache HIT for id={}", productId);

            return Optional.of(response);

        } catch (JacksonException ex) {

            log.warn("Invalid cached product data for id={}", productId, ex);
            deleteKey(key);
            return Optional.empty();

        } catch (DataAccessException ex) {

            log.warn("Redis unavailable while reading product id={}", productId, ex);
            return Optional.empty();
        }
    }

    public void put(ProductResponseDTO response) {

        String key = KEY_PREFIX + response.id();

        try {
            String json =
                    objectMapper.writeValueAsString(response);

            redisTemplate.opsForValue()
                    .set(key, json, CACHE_TTL);

            log.info("Product cached successfully for id={}", response.id());

        } catch (JacksonException ex) {

            log.warn("Could not serialize product id={}", response.id(), ex);

        } catch (DataAccessException ex) {

            log.warn("Redis unavailable while caching product id={}", response.id(), ex);
        }
    }

    public void evictAfterCommit(Long productId) {

        String key = KEY_PREFIX + productId;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        @Override
                        public void afterCommit() {
                            deleteKey(key);
                        }
                    }
            );

        } else {
            deleteKey(key);
        }
    }

    private void deleteKey(String key) {

        try {
            redisTemplate.delete(key);

        } catch (DataAccessException ex) {

            log.warn("Could not delete Redis key {}", key, ex);
        }
    }
}
