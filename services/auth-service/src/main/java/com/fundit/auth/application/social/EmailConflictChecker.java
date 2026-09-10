package com.fundit.auth.application.social;

import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 이메일이 이미 쓰이고 있을 때의 PM 확정 정책을 한 곳에서 판정한다.
 *
 * <p><b>진입점이 둘이라 공용으로 둔다.</b> 제공자가 이메일을 주면 소셜 <i>로그인</i> 시점에,
 * 주지 않으면(카카오는 이메일 동의가 선택) 사용자가 이메일을 입력하는 <i>가입</i> 시점에 판정하게 된다.
 * 두 곳에 각각 쓰면 언젠가 한쪽만 고쳐진다.
 *
 * <ul>
 *   <li><b>B·C</b> — 이미 소셜로 가입된 이메일이면 어느 제공자인지 알려주고 막는다.
 *       클라이언트가 문장을 파싱하지 않도록 detail에 provider를 기계가 읽는 값으로 싣는다.</li>
 *   <li><b>A</b> — 자체가입 계정이면 본인인증 후 연동할 수 있다. 그 흐름은 SocialLinkService가 맡고,
 *       여기서는 "연동 대상 계정"을 돌려주기만 한다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EmailConflictChecker {

    private final AccountRepository accountRepository;

    /**
     * @return 같은 이메일의 <b>자체가입</b> 계정(정책 A 대상). 충돌이 없으면 null
     * @throws BusinessException 이미 소셜로 가입된 이메일일 때(정책 B·C)
     */
    public Account checkOrThrow(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        Account existing = accountRepository.findByEmail(email).orElse(null);
        if (existing == null) {
            return null;
        }
        if (existing.getSocialProvider() != null) {
            throw new BusinessException(
                    AuthErrorCode.SOCIAL_ACCOUNT_EXISTS,
                    "이미 " + existing.getSocialProvider() + " 소셜 로그인 계정이 존재합니다.",
                    Map.of("provider", existing.getSocialProvider()));
        }
        return existing;
    }
}
