package com.example.bankcore.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis as a shared cache.
 *
 * <p>Why this replaces the in-memory cache from Phase 08: with two instances, each keeps its own
 * copy, so an eviction on one leaves the other serving stale reference data. A shared cache makes
 * the eviction shared too.
 *
 * <p>Two rules encoded here:
 * <ul>
 *   <li><b>Everything has a TTL.</b> A cache entry with no expiry is a memory leak with a
 *       plausible excuse, and it survives the bug that forgot to evict it.</li>
 *   <li><b>Nulls are not cached.</b> Caching "not found" turns a typo into a lasting answer.</li>
 * </ul>
 *
 * <p>Redis is never the source of truth for financial state — it is a cache and a coordination
 * tool, and PostgreSQL remains the record (`docs/architecture/overview.md`).
 */
@Configuration
@ConditionalOnProperty(name = "bankcore.redis.enabled", havingValue = "true")
public class RedisConfig {

    @Bean
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                // Reference data changes rarely and is evicted explicitly on every write, so it
                // can live longer than the default.
                .withCacheConfiguration("masterData", configuration.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("masterDataTypes", configuration.entryTtl(Duration.ofHours(1)))
                .build();
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
