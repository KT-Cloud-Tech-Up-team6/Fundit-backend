package com.fundit.auth.infrastructure.identity;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.common.error.DependencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedisIdentityVerificationStore implements IdentityVerificationStore {

    private static final String KEY_PREFIX = "identity-verification:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisIdentityVerificationStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String verificationToken, VerifiedIdentity identity, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(verificationToken), objectMapper.writeValueAsString(identity), ttl);
        } catch (DataAccessException e) {
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public Optional<VerifiedIdentity> consume(String verificationToken) {
        String json;
        try {
            json = redisTemplate.opsForValue().getAndDelete(key(verificationToken));
        } catch (DataAccessException e) {
            throw new DependencyFailureException(e);
        }
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(json, VerifiedIdentity.class));
    }

    private String key(String verificationToken) {
        return KEY_PREFIX + verificationToken;
    }
}
