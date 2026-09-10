package com.fundit.auth.application.social;

import com.fundit.auth.domain.account.SocialProvider;

/**
 * 소셜 제공자 연동 포트. 구현체는 제공자마다 하나씩 infrastructure/social에 있다 —
 * 인가 코드 교환은 같은 모양이지만 사용자정보 응답 구조가 서로 달라서 한 클래스로 묶지 않았다.
 */
public interface SocialProviderClient {

    SocialProvider provider();

    /**
     * 인가 코드를 액세스 토큰으로 바꾸고 사용자정보를 조회한다.
     * OAuth 인가 코드는 1회용이라 이 호출이 끝나면 같은 코드로 다시 부를 수 없다.
     */
    SocialIdentity fetchIdentity(String authorizationCode);

    /**
     * 제공자가 확인해 준 신원. {@code email}과 {@code name}은 <b>없을 수 있다</b> —
     * 카카오는 이메일 제공 동의가 선택이다. 없으면 가입 화면에서 사용자에게 직접 받는다.
     */
    record SocialIdentity(String socialId, String email, String name) {
    }
}
