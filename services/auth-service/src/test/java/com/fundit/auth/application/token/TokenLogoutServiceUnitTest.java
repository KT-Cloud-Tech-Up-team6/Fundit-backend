package com.fundit.auth.application.token;

import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenLogoutServiceUnitTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @InjectMocks
    private TokenLogoutService tokenLogoutService;

    @Test
    void 유효한_토큰이면_그_토큰만_폐기한다() {
        // given
        UUID tokenId = UUID.randomUUID();
        when(jwtTokenProvider.parseRefreshToken("raw-token"))
                .thenReturn(new JwtTokenProvider.RefreshTokenClaims(tokenId, UUID.randomUUID()));
        when(refreshTokenJpaRepository.deleteAndReturnAccountId(tokenId)).thenReturn(Optional.empty());

        // when
        tokenLogoutService.logout("raw-token");

        // then — 이미 폐기된 토큰이어도 전체 세션 폐기(재사용 탐지)는 하지 않는다
        verify(refreshTokenJpaRepository).deleteAndReturnAccountId(tokenId);
        verify(refreshTokenJpaRepository, never()).deleteAllByAccountId(any());
    }

    @Test
    void 쿠키가_없거나_무효한_토큰이면_아무것도_하지_않고_성공한다() {
        // given — 로그아웃은 멱등이다
        when(jwtTokenProvider.parseRefreshToken("broken"))
                .thenThrow(new BusinessException(CommonErrorCode.TOKEN_INVALID));

        // when
        tokenLogoutService.logout(null);
        tokenLogoutService.logout("broken");

        // then
        verify(refreshTokenJpaRepository, never()).deleteAndReturnAccountId(any());
    }
}
