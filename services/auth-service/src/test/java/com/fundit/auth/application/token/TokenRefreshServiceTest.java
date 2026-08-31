package com.fundit.auth.application.token;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRefreshServiceTest {

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
    void 정상_토큰이면_회전되어_새_토큰이_발급된다() {
        // given
        UUID tokenId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId).email("test@fundit.com").role(Role.MEMBER)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(jwtTokenProvider.parseRefreshToken("raw-token"))
                .thenReturn(new JwtTokenProvider.RefreshTokenClaims(tokenId, accountId));
        when(refreshTokenJpaRepository.deleteAndReturnAccountId(tokenId)).thenReturn(Optional.of(accountId));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(tokenIssuer.issue(accountId, Role.MEMBER))
                .thenReturn(new TokenIssuer.IssuedTokens("new-access", "new-refresh"));

        // when
        TokenIssuer.IssuedTokens result = tokenRefreshService.refresh("raw-token");

        // then
        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
    }
}
