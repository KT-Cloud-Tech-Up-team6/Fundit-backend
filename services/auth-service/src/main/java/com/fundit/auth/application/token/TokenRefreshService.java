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

    /**
     * 이 메서드 자체를 @Transactional로 감싸지 않는다 — 감싸면 재사용 탐지 시 던지는
     * BusinessException 때문에 트랜잭션이 롤백되면서, 방금 실행한 전체 세션 무효화
     * (deleteAllByAccountId)까지 같이 취소돼버린다(실기동으로 재현·확인, 2026-08-31:
     * 탈취된 토큰은 막히는데 정상 로테이션된 토큰은 살아남는 상태였음). 대신
     * deleteAllByAccountId는 RefreshTokenJpaRepository 쪽에 자체 @Transactional을 둬서,
     * 이후 무슨 예외가 나든 그 삭제만은 독립적으로 즉시 커밋되게 한다.
     */
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
