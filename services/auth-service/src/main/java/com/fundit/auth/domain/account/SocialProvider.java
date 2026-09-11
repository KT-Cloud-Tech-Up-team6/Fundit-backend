package com.fundit.auth.domain.account;

/**
 * 소셜 로그인 제공자. {@code accounts.social_provider} 컬럼에 이름 그대로 저장한다
 * (V1__init_schema.sql 주석: "KAKAO / GOOGLE, 자체가입은 NULL").
 */
public enum SocialProvider {
    KAKAO,
    GOOGLE
}
