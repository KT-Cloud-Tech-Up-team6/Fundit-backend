package com.fundit.auth.presentation.dto;

import com.fundit.auth.application.social.SocialLoginService;
import com.fundit.auth.domain.account.SocialProvider;

/**
 * 가입이 필요한 경우와 로그인된 경우를 한 형태로 내려준다 — {@code needsSignup}으로 분기한다.
 * 명세 AUTH-002가 두 응답 모두 200으로 정의한다: 미가입을 404로 주면 프론트가 에러 처리 경로로
 * 빠져 가입 화면 전환이 어색해진다.
 *
 * <p>application.yml의 {@code default-property-inclusion: non_null} 때문에 null 필드는
 * 응답 JSON에서 빠진다 — 로그인 응답에 signupToken이 보이지 않는다.
 */
public record SocialLoginResponse(
        boolean needsSignup,
        String accessToken,
        Boolean mustChangePassword,
        SocialProvider provider,
        String signupToken,
        String email,
        String name
) {

    public static SocialLoginResponse from(SocialLoginService.SocialLoginResult result) {
        if (result.needsSignup()) {
            return new SocialLoginResponse(true, null, null,
                    result.provider(), result.signupToken(), result.email(), result.name());
        }
        return new SocialLoginResponse(false, result.accessToken(), result.mustChangePassword(),
                null, null, null, null);
    }
}
