package com.fundit.auth.application.token;

import com.fundit.auth.domain.account.AccountRepository;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRefreshServiceUnitExceptionTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenJpaRepository refreshTokenJpaRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TokenIssuer tokenIssuer;

    @InjectMocks
    private TokenRefreshService tokenRefreshService;

    @Test
    void 서명이_무효하면_DB조회_없이_예외가_전파된다() {
        // given
        when(jwtTokenProvider.parseRefreshToken("bad-token"))
                .thenThrow(new BusinessException(CommonErrorCode.TOKEN_INVALID));

        // when & then
        assertThatThrownBy(() -> tokenRefreshService.refresh("bad-token"))
                .isInstanceOf(BusinessException.class);
        verify(refreshTokenJpaRepository, never()).deleteAndReturnAccountId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 이미_폐기된_토큰이_재사용되면_전체세션이_무효화되고_예외가_발생한다() {
        // given
        UUID tokenId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(jwtTokenProvider.parseRefreshToken("stolen-token"))
                .thenReturn(new JwtTokenProvider.RefreshTokenClaims(tokenId, accountId));
        when(refreshTokenJpaRepository.deleteAndReturnAccountId(tokenId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tokenRefreshService.refresh("stolen-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
        verify(refreshTokenJpaRepository).deleteAllByAccountId(accountId);
    }

    @Test
    void 회전은_성공했지만_계정이_존재하지_않으면_예외가_발생한다() {
        // given
        UUID tokenId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(jwtTokenProvider.parseRefreshToken("raw-token"))
                .thenReturn(new JwtTokenProvider.RefreshTokenClaims(tokenId, accountId));
        when(refreshTokenJpaRepository.deleteAndReturnAccountId(tokenId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tokenRefreshService.refresh("raw-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
    }
}
