
package com.orderengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template =
                new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);

        // Serialize Redis keys as readable strings
        template.setKeySerializer(
                new StringRedisSerializer());

        // Serialize values as JSON using Jackson 3

        template.setHashKeySerializer(
                new StringRedisSerializer());

        template.setValueSerializer(
                GenericJacksonJsonRedisSerializer.create(builder -> {})
        );

        template.setHashValueSerializer(
                GenericJacksonJsonRedisSerializer.create(builder -> {})
        );

        template.afterPropertiesSet();

        return template;
    }
}
