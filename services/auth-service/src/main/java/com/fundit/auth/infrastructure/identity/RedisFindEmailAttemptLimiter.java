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
 * <p>매 호출마다 TTL을 다시 건다(슬라이딩 윈도) — {@code attempts==1}일 때만 걸면, 그 직후
 * 프로세스가 죽거나 예외가 나면 TTL 없는 키가 남아 그 번호는 영원히 429가 된다. 슬라이딩으로
 * 바꾸면 "마지막 시도로부터 10분"이 되어 원자성 문제가 사라지고, 열거 지연이라는 목적에도
 * 더 맞는다(계속 시도하는 공격자는 window가 계속 밀린다).
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
            redisTemplate.expire(key, WINDOW);
            return attempts != null && attempts > MAX_ATTEMPTS;
        } catch (DataAccessException e) {
            throw new DependencyFailureException(e);
        }
    }
}
