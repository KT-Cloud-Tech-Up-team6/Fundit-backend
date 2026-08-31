package com.fundit.auth.infrastructure.security;

import com.fundit.auth.domain.account.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderUnitTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-secret-key-at-least-32-bytes-long!!");
        properties.setAccessTokenTtl(Duration.ofMinutes(30));
        properties.setRefreshTokenTtl(Duration.ofDays(14));
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void 발급한_access_token을_파싱하면_동일한_클레임을_얻는다() {
        // given
        UUID accountId = UUID.randomUUID();

        // when
        String token = jwtTokenProvider.issueAccessToken(accountId, Role.ADMIN);
        var claims = jwtTokenProvider.parseAccessToken(token);

        // then
        assertThat(claims.accountId()).isEqualTo(accountId);
        assertThat(claims.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void 발급한_refresh_token을_파싱하면_동일한_클레임을_얻는다() {
        // given
        UUID tokenId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        // when
        String token = jwtTokenProvider.issueRefreshToken(tokenId, accountId);
        var claims = jwtTokenProvider.parseRefreshToken(token);

        // then
        assertThat(claims.tokenId()).isEqualTo(tokenId);
        assertThat(claims.accountId()).isEqualTo(accountId);
    }
}
