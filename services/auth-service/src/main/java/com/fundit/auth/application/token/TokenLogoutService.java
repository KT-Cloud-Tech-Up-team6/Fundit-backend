package com.fundit.auth.application.token;

import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import com.fundit.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그아웃 — 제출된 refresh 토큰 하나만 폐기한다.
 *
 * <p><b>멱등이다.</b> 토큰이 없거나 무효·만료·이미 폐기돼도 성공으로 끝낸다 — 로그아웃은
 * "이 기기의 세션을 끝낸다"가 목적이라 결과가 같으면 에러를 낼 이유가 없다. 특히 이미 폐기된
 * 토큰이어도 재사용 탐지(전체 세션 폐기, {@link TokenRefreshService})를 트리거하지 않는다 —
 * 로그아웃 버튼을 두 번 눌렀다고 다른 기기까지 로그아웃되면 안 된다.
 */
@Service
@RequiredArgsConstructor
public class TokenLogoutService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        JwtTokenProvider.RefreshTokenClaims claims;
        try {
            claims = jwtTokenProvider.parseRefreshToken(refreshToken);
        } catch (BusinessException e) {
            return;
        }
        refreshTokenJpaRepository.deleteAndReturnAccountId(claims.tokenId());
    }
}
