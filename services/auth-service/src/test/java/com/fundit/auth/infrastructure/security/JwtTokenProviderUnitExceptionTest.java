package com.fundit.auth.infrastructure.security;

import com.fundit.auth.domain.account.Role;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderUnitExceptionTest {

    private JwtTokenProvider provider(Duration accessTtl) {
        return JwtTestKeys.provider(JwtTestKeys.PRIVATE_KEY, accessTtl);
    }

    @Test
    void 다른_키페어로_발급된_토큰이면_TOKEN_INVALID_예외가_발생한다() {
        // given
        JwtTokenProvider issuer = JwtTestKeys.provider(JwtTestKeys.OTHER_PRIVATE_KEY, Duration.ofMinutes(30));
        JwtTokenProvider verifier = provider(Duration.ofMinutes(30));
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
        JwtTokenProvider provider = provider(Duration.ofSeconds(-1));
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
        JwtTokenProvider provider = provider(Duration.ofMinutes(30));

        // when & then
        assertThatThrownBy(() -> provider.parseAccessToken("not-a-jwt"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
    }

    @Test
    void refresh_token을_access_token_파서에_넣으면_TOKEN_INVALID_예외가_발생한다() {
        // given
        JwtTokenProvider provider = provider(Duration.ofMinutes(30));
        String refreshToken = provider.issueRefreshToken(UUID.randomUUID(), UUID.randomUUID());

        // when & then
        assertThatThrownBy(() -> provider.parseAccessToken(refreshToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
    }

    @Test
    void access_token을_refresh_token_파서에_넣으면_TOKEN_INVALID_예외가_발생한다() {
        // given
        JwtTokenProvider provider = provider(Duration.ofMinutes(30));
        String accessToken = provider.issueAccessToken(UUID.randomUUID(), Role.MEMBER);

        // when & then
        assertThatThrownBy(() -> provider.parseRefreshToken(accessToken))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
    }
}
