package com.fundit.auth.infrastructure.social;

import com.fundit.auth.application.social.SocialTokenStore;
import com.fundit.common.error.DependencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/** RedisIdentityVerificationStore와 같은 패턴 — 짧은 TTL + 1회 소비. */
@Component
public class RedisSocialTokenStore implements SocialTokenStore {

    private static final String SIGNUP_PREFIX = "social-signup:";
    private static final String LINK_PREFIX = "social-link:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSocialTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveSignup(String signupToken, PendingSocialSignup pending, Duration ttl) {
        save(SIGNUP_PREFIX + signupToken, pending, ttl);
    }

    @Override
    public Optional<PendingSocialSignup> consumeSignup(String signupToken) {
        return consume(SIGNUP_PREFIX + signupToken, PendingSocialSignup.class);
    }

    @Override
    public void saveLink(String linkToken, PendingSocialLink pending, Duration ttl) {
        save(LINK_PREFIX + linkToken, pending, ttl);
    }

    @Override
    public Optional<PendingSocialLink> consumeLink(String linkToken) {
        return consume(LINK_PREFIX + linkToken, PendingSocialLink.class);
    }

    private void save(String key, Object pending, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(pending), ttl);
        } catch (DataAccessException e) {
            throw new DependencyFailureException(e);
        }
    }

    private <T> Optional<T> consume(String key, Class<T> type) {
        String json;
        try {
            json = redisTemplate.opsForValue().getAndDelete(key);
        } catch (DataAccessException e) {
            throw new DependencyFailureException(e);
        }
        return Optional.ofNullable(json).map(value -> objectMapper.readValue(value, type));
    }
}
