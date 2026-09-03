package com.fundit.auth.application.token;

import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaEntity;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import com.fundit.auth.infrastructure.security.JwtProperties;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenIssuerUnitTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @InjectMocks
    private TokenIssuer tokenIssuer;

    @Test
    void access와_refresh_토큰을_발급하고_refresh_token을_저장한다() {
        // given
        UUID accountId = UUID.randomUUID();
        when(jwtTokenProvider.issueAccessToken(accountId, Role.MEMBER)).thenReturn("access-token");
        when(jwtTokenProvider.issueRefreshToken(any(), org.mockito.ArgumentMatchers.eq(accountId)))
                .thenReturn("refresh-token");
        when(jwtProperties.getRefreshTokenTtl()).thenReturn(Duration.ofDays(14));

        // when
        TokenIssuer.IssuedTokens result = tokenIssuer.issue(accountId, Role.MEMBER);

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<RefreshTokenJpaEntity> captor = ArgumentCaptor.forClass(RefreshTokenJpaEntity.class);
        verify(refreshTokenJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(accountId);
    }
}
