package com.routeassign.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for RouteAssign.
 *
 * Connection: localhost:6379 (configured in application.properties).
 *
 * Serialization strategy:
 *   Keys   → plain String  (human-readable in redis-cli)
 *   Values → JSON via GenericJackson2JsonRedisSerializer
 *            (includes @class type hint so objects deserialise correctly)
 *
 * TTL values are read from application.properties:
 *   routeassign.cache.rule-ttl-seconds    (default 300 s = 5 min)
 *   routeassign.cache.scoring-ttl-seconds (default 300 s = 5 min)
 *
 * TTL is a safety backstop — primary consistency comes from explicit
 * cache eviction in the service write paths.
 */
@Configuration
public class RedisConfig {

    @Value("${routeassign.cache.rule-ttl-seconds:300}")
    private long ruleTtlSeconds;

    @Value("${routeassign.cache.scoring-ttl-seconds:300}")
    private long scoringTtlSeconds;

    public long getRuleTtlSeconds()    { return ruleTtlSeconds; }
    public long getScoringTtlSeconds() { return scoringTtlSeconds; }

    /**
     * Primary RedisTemplate used by {@code RuleCacheService}.
     *
     * Key serializer   : StringRedisSerializer  → readable keys in redis-cli
     * Value serializer : GenericJackson2JsonRedisSerializer → JSON with type info
     *
     * The ObjectMapper includes:
     *  - JavaTimeModule   (handles LocalDateTime / LocalDate)
     *  - activateDefaultTyping  (adds @class so Jackson can deserialise back)
     *  - WRITE_DATES_AS_TIMESTAMPS disabled (ISO-8601 strings, not epoch longs)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
