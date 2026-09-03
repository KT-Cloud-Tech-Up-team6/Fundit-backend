package com.fundit.auth.application.token;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;
    private final AccountRepository accountRepository;
    private final TokenIssuer tokenIssuer;

    /**
     * 계정 단위 pessimistic lock으로 동시 refresh 요청을 직렬화한다 — 그래야 "재사용 탐지 시
     * 전체 세션 무효화" 보장이 동시성 상황에서도 깨지지 않는다(PR 리뷰 지적, 2026-09-03: 락 없이는
     * 요청 A가 로테이션에 성공해 새 토큰을 저장하는 도중 요청 B가 같은 옛 토큰으로 재사용 탐지를
     * 트리거하면, B의 deleteAllByAccountId가 A의 저장보다 먼저 커밋될 수 있어 A의 새 세션이
     * 무효화를 피해 살아남을 수 있었다).
     *
     * 이 메서드를 @Transactional로 감싸도 안전한 이유: 재사용 탐지 시 실행하는
     * deleteAllByAccountId가 RefreshTokenJpaRepository 쪽에서 REQUIRES_NEW로 독립 커밋되므로,
     * 아래에서 던지는 BusinessException으로 이 메서드의 트랜잭션이 롤백돼도 그 삭제는 살아남는다
     * (이 트랜잭션 자체는 락 획득/해제 범위로만 쓰인다 — 버그3 때와 같은 함정이 아니다).
     */
    @Transactional
    public TokenIssuer.IssuedTokens refresh(String refreshToken) {
        // 서명 무효/만료는 여기서 즉시 예외 — DB 조회 없이 401
        var claims = jwtTokenProvider.parseRefreshToken(refreshToken);

        // 계정 행을 잠가서, 같은 계정에 대한 동시 refresh 요청은 이 트랜잭션이 끝날 때까지 대기한다.
        accountRepository.lockForUpdate(claims.accountId());

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
