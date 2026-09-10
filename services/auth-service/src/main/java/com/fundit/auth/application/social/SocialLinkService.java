package com.fundit.auth.application.social;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.application.signup.MemberServiceClient;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountLockedException;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 기존 자체가입 계정에 소셜 로그인을 붙인다(PM 확정 정책 A).
 *
 * <p><b>이 클래스의 전부는 "정말 본인인가"를 확인하는 것이다.</b> 여기가 뚫리면 피해자의 이메일만
 * 알면 소셜 계정을 붙여 로그인할 수 있다. 그래서 두 가지를 지킨다:
 *
 * <ul>
 *   <li>연동 대상 계정은 {@code linkToken}에서만 나온다 — 클라이언트가 accountId를 보내지 않는다.
 *       보내게 하면 본인인증만 통과하면 아무 계정이나 지목할 수 있다.</li>
 *   <li>본인인증으로 확인된 휴대폰번호가 그 계정 주인의 것인지 member-service에 확인한다.
 *       auth-service는 휴대폰번호를 보관하지 않으므로(설계상) 여기서만 판단할 수 없다.</li>
 * </ul>
 *
 * <p>연동 후에도 기존 비밀번호는 그대로 둔다 — 일반 로그인과 소셜 로그인이 둘 다 되어야 한다.
 */
@Service
@RequiredArgsConstructor
public class SocialLinkService {

    private final AccountRepository accountRepository;
    private final SocialTokenStore socialTokenStore;
    private final IdentityVerificationStore identityVerificationStore;
    private final MemberServiceClient memberServiceClient;
    private final TokenIssuer tokenIssuer;

    public TokenIssuer.IssuedTokens link(String linkToken, String verificationToken) {
        var pending = socialTokenStore.consumeLink(linkToken)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.TOKEN_EXPIRED));

        var verifiedIdentity = identityVerificationStore.consume(verificationToken)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.TOKEN_INVALID));

        if (!memberServiceClient.phoneMatches(pending.accountId(), verifiedIdentity.phoneNumber())) {
            // 본인이 아니다. "계정 없음"과 "번호 다름"을 구분해 알려주지 않는다.
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "본인 확인에 실패했습니다.");
        }

        Account account = accountRepository.findById(pending.accountId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        // 잠긴 계정에는 토큰을 내주지 않는다 — 일반 로그인/소셜 로그인과 같은 규칙이다.
        // 본인인증을 통과했더라도 locked_until은 계정 상태라, 여기만 열어두면 잠금을 우회할 수 있다.
        if (account.isLocked(Instant.now())) {
            throw new AccountLockedException(account.getLockedUntil());
        }

        account.linkSocial(pending.provider(), pending.socialId());
        accountRepository.save(account);

        return tokenIssuer.issue(account.getId(), account.getRole());
    }
}
