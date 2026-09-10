package com.fundit.auth.application.social;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.domain.account.SocialProvider;

import java.time.Duration;
import java.util.Optional;

/**
 * 소셜 로그인에서 "가입이 필요하다"고 판정된 신원을 잠시 보관한다.
 *
 * <p>왜 필요한가: OAuth 인가 코드는 1회용이라 가입 단계에서 다시 쓸 수 없다. 이미 검증한 신원을
 * 서버가 들고 있다가 가입 요청 때 꺼내 쓴다 — 클라이언트가 socialId를 들고 왔다 다시 보내는
 * 방식이면 아무 값이나 주장할 수 있다.
 *
 * <p>{@link IdentityVerificationStore}와 같은 1회 소비 패턴이다.
 */
public interface SocialSignupTokenStore {

    void save(String signupToken, PendingSocialSignup pending, Duration ttl);

    /** 1회 소비(get-and-delete) — 같은 토큰으로 두 번 꺼내면 두 번째는 항상 비어있다. */
    Optional<PendingSocialSignup> consume(String signupToken);

    /**
     * 제공자가 확인해 준 신원. {@code email}/{@code name}은 null일 수 있다
     * (카카오는 이메일 동의가 선택) — 없으면 가입 화면에서 사용자에게 받는다.
     */
    record PendingSocialSignup(SocialProvider provider, String socialId, String email, String name) {
    }
}
