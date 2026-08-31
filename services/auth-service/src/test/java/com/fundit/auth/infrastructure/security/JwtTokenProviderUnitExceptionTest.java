package com.fundit.auth.infrastructure.security;

import com.fundit.auth.domain.account.Role;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderUnitExceptionTest {

    private JwtTokenProvider provider(String secret, Duration accessTtl) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessTokenTtl(accessTtl);
        properties.setRefreshTokenTtl(Duration.ofDays(14));
        return new JwtTokenProvider(properties);
    }

    @Test
    void 서명이_다른_키로_발급된_토큰이면_TOKEN_INVALID_예외가_발생한다() {
        // given
        JwtTokenProvider issuer = provider("issuer-secret-key-at-least-32-bytes-long!!!", Duration.ofMinutes(30));
        JwtTokenProvider verifier = provider("verifier-secret-key-at-least-32-bytes-long!", Duration.ofMinutes(30));
        String token = issuer.issueAccessToken(UUID.randomUUID(), Role.MEMBER);

        // when & then
        assertThatThrownBy(() -> verifier.parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
    }

    @Test
    void 만료된_토큰이면_TOKEN_EXPIRED_예외가_발생한다() {
        // given
        JwtTokenProvider provider = provider("expired-token-secret-key-at-least-32-bytes!", Duration.ofSeconds(-1));
        String token = provider.issueAccessToken(UUID.randomUUID(), Role.MEMBER);

        // when & then
        assertThatThrownBy(() -> provider.parseAccessToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 형식이_깨진_토큰이면_TOKEN_INVALID_예외가_발생한다() {
        // given
        JwtTokenProvider provider = provider("malformed-token-secret-key-at-least-32-bytes", Duration.ofMinutes(30));

        // when & then
        assertThatThrownBy(() -> provider.parseAccessToken("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
    }
}
