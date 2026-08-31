package com.fundit.auth.application.token;

import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaEntity;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import com.fundit.auth.infrastructure.security.JwtProperties;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Access+Refresh Token 발급 및 refresh_tokens 저장 — 로그인/회원가입/토큰재발급이 공통으로 쓴다. */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    public IssuedTokens issue(UUID accountId, Role role) {
        Instant now = Instant.now();
        String accessToken = jwtTokenProvider.issueAccessToken(accountId, role);

        UUID tokenId = UUID.randomUUID();
        String refreshToken = jwtTokenProvider.issueRefreshToken(tokenId, accountId);
        refreshTokenJpaRepository.save(RefreshTokenJpaEntity.builder()
                .tokenId(tokenId)
                .accountId(accountId)
                .expiresAt(now.plus(jwtProperties.getRefreshTokenTtl()))
                .createdAt(now)
                .build());

        return new IssuedTokens(accessToken, refreshToken);
    }

    public record IssuedTokens(String accessToken, String refreshToken) {
    }
}
