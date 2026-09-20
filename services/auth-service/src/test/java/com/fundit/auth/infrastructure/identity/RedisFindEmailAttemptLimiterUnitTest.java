package com.fundit.auth.infrastructure.identity;

import com.fundit.auth.infrastructure.security.BlindIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisFindEmailAttemptLimiterUnitTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisFindEmailAttemptLimiter limiter() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        return new RedisFindEmailAttemptLimiter(redisTemplate, new BlindIndex(KEY));
    }

    @Test
    void 한도_이내면_통과한다() {
        // given
        given(valueOperations.increment(anyString())).willReturn(3L);

        // when
        boolean exceeded = limiter().exceeded("01012345678");

        // then
        assertThat(exceeded).isFalse();
    }

    @Test
    void 한도를_넘으면_막는다() {
        // given
        given(valueOperations.increment(anyString())).willReturn(6L);

        // when
        boolean exceeded = limiter().exceeded("01012345678");

        // then
        assertThat(exceeded).isTrue();
    }

    @Test
    void 매_시도마다_TTL을_다시_건다() {
        // given — attempts==1일 때만 걸면, 그 직후 죽으면 TTL 없는 키가 영원히 남는다.
        // 슬라이딩 윈도로 바꿔 매번 건다
        given(valueOperations.increment(anyString())).willReturn(1L, 2L, 3L);
        RedisFindEmailAttemptLimiter limiter = limiter();

        // when
        limiter.exceeded("01012345678");
        limiter.exceeded("01012345678");
        limiter.exceeded("01012345678");

        // then
        verify(redisTemplate, times(3)).expire(anyString(), any(Duration.class));
    }

    @Test
    void 전화번호_원문이_아니라_해시를_키로_쓴다() {
        // given — Redis에 번호가 그대로 쌓이면 그 자체가 개인정보 보관이 된다(S9)
        given(valueOperations.increment(anyString())).willReturn(1L);

        // when
        limiter().exceeded("01012345678");

        // then
        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).doesNotContain("01012345678");
    }
}
