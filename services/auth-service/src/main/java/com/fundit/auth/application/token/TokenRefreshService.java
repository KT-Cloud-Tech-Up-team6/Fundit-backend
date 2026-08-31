package com.fundit.auth.application.token;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;
    private final AccountRepository accountRepository;
    private final TokenIssuer tokenIssuer;

    public TokenIssuer.IssuedTokens refresh(String refreshToken) {
        // 서명 무효/만료는 여기서 즉시 예외 — DB 조회 없이 401
        var claims = jwtTokenProvider.parseRefreshToken(refreshToken);

        Optional<UUID> rotatedAccountId = refreshTokenJpaRepository.deleteAndReturnAccountId(claims.tokenId());
        if (rotatedAccountId.isEmpty()) {
            // 재사용 탐지: 이미 폐기된 토큰이 다시 제출됨 = 탈취 의심 → 해당 계정 전체 세션 강제 로그아웃
            refreshTokenJpaRepository.deleteAllByAccountId(claims.accountId());
            throw new BusinessException(CommonErrorCode.TOKEN_INVALID);
        }

        Account account = accountRepository.findById(rotatedAccountId.get())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.TOKEN_INVALID));

        return tokenIssuer.issue(account.getId(), account.getRole());
    }
}
