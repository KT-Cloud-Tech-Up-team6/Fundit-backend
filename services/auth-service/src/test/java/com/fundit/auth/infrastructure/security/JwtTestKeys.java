package com.fundit.auth.infrastructure.security;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;

/**
 * 테스트용 RSA 키. 클래스 로드 시 한 번 생성한다 — 레포에 개인키를 커밋하지 않기 위함이다.
 *
 * <p>{@code @TestPropertySource}는 컴파일 상수(리터럴)만 받아서 생성한 키를 넣을 수 없다.
 * 그래서 스프링 컨텍스트를 띄우는 테스트는 {@link #register(DynamicPropertyRegistry)}를
 * {@code @DynamicPropertySource}로 호출한다.
 */
public final class JwtTestKeys {

    /** base64(PKCS#8 DER) — jwt.private-key에 그대로 넣는 형식. */
    public static final String PRIVATE_KEY = generateBase64Pkcs8();

    /** 위조 서명 테스트용 — PRIVATE_KEY와 다른 키페어. */
    public static final String OTHER_PRIVATE_KEY = generateBase64Pkcs8();

    private JwtTestKeys() {
    }

    /** 스프링 컨텍스트를 띄우는 테스트에서 @DynamicPropertySource로 호출한다. */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("jwt.private-key", () -> PRIVATE_KEY);
        registry.add("jwt.access-token-ttl", () -> "30m");
        registry.add("jwt.refresh-token-ttl", () -> "14d");
    }

    /** 컨텍스트 없이 JwtTokenProvider를 직접 만드는 단위 테스트용. */
    public static JwtProperties properties(String base64PrivateKey, Duration accessTokenTtl) {
        JwtProperties properties = new JwtProperties();
        properties.setPrivateKey(base64PrivateKey);
        properties.setAccessTokenTtl(accessTokenTtl);
        properties.setRefreshTokenTtl(Duration.ofDays(14));
        return properties;
    }

    public static JwtTokenProvider provider(String base64PrivateKey, Duration accessTokenTtl) {
        return new JwtTokenProvider(properties(base64PrivateKey, accessTokenTtl));
    }

    private static String generateBase64Pkcs8() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            // getEncoded()가 PKCS#8 DER을 돌려준다 — 운영에서 주입받는 형식과 동일하다.
            return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("테스트 RSA 키 생성 실패", e);
        }
    }
}
