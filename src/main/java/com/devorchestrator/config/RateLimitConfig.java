package com.devorchestrator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Slf4j
public class RateLimitConfig {

    @Value("${app.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${app.rate-limit.burst-capacity:10}")
    private int burstCapacity;

    @Bean
    public RedisTemplate<String, Object> rateLimitRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RateLimitService rateLimitService() {
        return new RateLimitService(requestsPerMinute, burstCapacity);
    }

    public static class RateLimitService {
        private final ConcurrentHashMap<String, RateLimitBucket> cache = new ConcurrentHashMap<>();
        private final int requestsPerMinute;
        private final int burstCapacity;

        public RateLimitService(int requestsPerMinute, int burstCapacity) {
            this.requestsPerMinute = requestsPerMinute;
            this.burstCapacity = burstCapacity;
        }

        public RateLimitBucket createNewBucket() {
            return new RateLimitBucket(requestsPerMinute, burstCapacity);
        }

        public RateLimitBucket getBucket(String key) {
            return cache.computeIfAbsent(key, k -> createNewBucket());
        }

        public boolean tryConsume(String key) {
            return getBucket(key).tryConsume(1);
        }

        public boolean tryConsume(String key, long tokens) {
            return getBucket(key).tryConsume(tokens);
        }

        public long getAvailableTokens(String key) {
            return getBucket(key).getAvailableTokens();
        }

        public void removeBucket(String key) {
            cache.remove(key);
        }

        public void clearAll() {
            cache.clear();
        }
    }
    
    public static class RateLimitBucket {
        private final int maxTokens;
        private final int refillRate;
        private int currentTokens;
        private LocalDateTime lastRefillTime;

        public RateLimitBucket(int maxTokens, int refillRate) {
            this.maxTokens = maxTokens;
            this.refillRate = refillRate;
            this.currentTokens = maxTokens;
            this.lastRefillTime = LocalDateTime.now();
        }

        public synchronized boolean tryConsume(long tokens) {
            refillTokens();
            if (currentTokens >= tokens) {
                currentTokens -= tokens;
                return true;
            }
            return false;
        }

        public synchronized long getAvailableTokens() {
            refillTokens();
            return currentTokens;
        }

        private void refillTokens() {
            LocalDateTime now = LocalDateTime.now();
            long minutesPassed = ChronoUnit.MINUTES.between(lastRefillTime, now);
            
            if (minutesPassed > 0) {
                int tokensToAdd = (int) Math.min(minutesPassed * refillRate, maxTokens - currentTokens);
                currentTokens = Math.min(currentTokens + tokensToAdd, maxTokens);
                lastRefillTime = now;
            }
        }
    }
}