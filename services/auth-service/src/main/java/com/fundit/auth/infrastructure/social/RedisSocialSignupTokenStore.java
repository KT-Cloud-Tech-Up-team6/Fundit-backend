package com.fundit.auth.infrastructure.social;

import com.fundit.auth.application.social.SocialSignupTokenStore;
import com.fundit.common.error.DependencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/** RedisIdentityVerificationStore와 같은 패턴 — 짧은 TTL + 1회 소비. */
@Component
public class RedisSocialSignupTokenStore implements SocialSignupTokenStore {

    private static final String KEY_PREFIX = "social-signup:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSocialSignupTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String signupToken, PendingSocialSignup pending, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(signupToken), objectMapper.writeValueAsString(pending), ttl);
        } catch (DataAccessException e) {
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public Optional<PendingSocialSignup> consume(String signupToken) {
        String json;
        try {
            json = redisTemplate.opsForValue().getAndDelete(key(signupToken));
        } catch (DataAccessException e) {
            throw new DependencyFailureException(e);
        }
        return Optional.ofNullable(json).map(value -> objectMapper.readValue(value, PendingSocialSignup.class));
    }

    private String key(String signupToken) {
        return KEY_PREFIX + signupToken;
    }
}
