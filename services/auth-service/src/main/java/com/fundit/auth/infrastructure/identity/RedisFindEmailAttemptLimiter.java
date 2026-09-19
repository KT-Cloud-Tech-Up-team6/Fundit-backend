package com.fundit.auth.infrastructure.identity;

import com.fundit.auth.application.email.FindEmailAttemptLimiter;
import com.fundit.auth.infrastructure.security.BlindIndex;
import com.fundit.common.error.DependencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@code INCR} + {@code EXPIRE}로 세는 고정 윈도 제한. 라이브러리를 새로 넣지 않는다 —
 * Redis는 본인인증 임시저장으로 이미 쓰고 있다.
 *
 * <p><b>키에 전화번호 원문을 쓰지 않는다.</b> Redis에 번호가 그대로 쌓이면 그 자체가
 * 개인정보 보관이 된다(security.md S9). 블라인드 인덱스 해시를 키로 쓴다.
 *
 * <p>ponytail: 고정 윈도라 경계에서 최대 2배까지 통과한다. 열거를 늦추는 게 목적이라
 * 이 정도면 충분하고, 정확한 제한이 필요해지면 슬라이딩 윈도로 바꾼다.
 */
@Component
public class RedisFindEmailAttemptLimiter implements FindEmailAttemptLimiter {

    private static final String KEY_PREFIX = "find-email-attempt:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final BlindIndex blindIndex;

    public RedisFindEmailAttemptLimiter(StringRedisTemplate redisTemplate, BlindIndex blindIndex) {
        this.redisTemplate = redisTemplate;
        this.blindIndex = blindIndex;
    }

    @Override
    public boolean exceeded(String phoneNumber) {
        String key = KEY_PREFIX + blindIndex.of(BlindIndex.LABEL_PHONE, phoneNumber);
        try {
            Long attempts = redisTemplate.opsForValue().increment(key);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
            return attempts != null && attempts > MAX_ATTEMPTS;
        } catch (DataAccessException e) {
            throw new DependencyFailureException(e);
        }
    }
}
