package com.fundit.auth.presentation.dto;

import com.fundit.auth.application.social.SocialLoginService;
import com.fundit.auth.domain.account.SocialProvider;

/**
 * 로그인 / 가입 필요 / 연동 필요를 한 형태로 내려준다 — {@code needsSignup}·{@code needsLink}로 분기한다.
 * 명세 AUTH-002가 두 응답 모두 200으로 정의한다: 미가입을 404로 주면 프론트가 에러 처리 경로로
 * 빠져 가입 화면 전환이 어색해진다.
 *
 * <p>application.yml의 {@code default-property-inclusion: non_null} 때문에 null 필드는
 * 응답 JSON에서 빠진다 — 로그인 응답에 signupToken이 보이지 않는다.
 */
public record SocialLoginResponse(
        boolean needsSignup,
        boolean needsLink,
        String linkToken,
        String accessToken,
        Boolean mustChangePassword,
        SocialProvider provider,
        String signupToken,
        String email,
        String name
) {

    public static SocialLoginResponse from(SocialLoginService.SocialLoginResult result) {
        if (result.needsSignup()) {
            return new SocialLoginResponse(true, false, null, null, null,
                    result.provider(), result.signupToken(), result.email(), result.name());
        }
        if (result.needsLink()) {
            // 이메일을 담지 않는다 — 소셜 로그인 시도만으로 타인의 가입 이메일이 드러나면 안 된다
            return new SocialLoginResponse(false, true, result.linkToken(), null, null,
                    result.provider(), null, null, null);
        }
        return new SocialLoginResponse(false, false, null, result.accessToken(), result.mustChangePassword(),
                null, null, null, null);
    }
}
