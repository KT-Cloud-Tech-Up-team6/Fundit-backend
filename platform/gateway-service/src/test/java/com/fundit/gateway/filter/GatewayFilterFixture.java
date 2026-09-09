package com.fundit.gateway.filter;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import tools.jackson.databind.ObjectMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 게이트웨이 필터 테스트용 픽스처. auth-service가 실제로 발급하는 것과 같은 형태의
 * RS256 토큰(sub/role/typ 클레임)을 만들어, 목이 아니라 진짜 서명 검증을 거치게 한다.
 *
 * <p>검증기는 {@code withJwkSetUri}가 아니라 {@code withPublicKey}를 쓴다 — 단위 테스트라
 * JWKS를 HTTP로 가져올 필요가 없다. 실제 JWKS 조회 배선은 {@code JwtDecoderConfig}에 있고,
 * 이 테스트가 검증하는 건 필터 로직이지 키를 어디서 가져오는지가 아니다.
 */
final class GatewayFilterFixture {

    private static final KeyPair KEY_PAIR = generateKeyPair();
    /** 위조 서명 검증용 — 위와 다른 키페어. */
    private static final KeyPair OTHER_KEY_PAIR = generateKeyPair();

    private GatewayFilterFixture() {
    }

    static JwtHeaderGlobalFilter filter(String internalApiKey) {
        return new JwtHeaderGlobalFilter(decoder(), new ObjectMapper(), internalApiKey);
    }

    static ReactiveJwtDecoder decoder() {
        return NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    static String accessToken(UUID accountId, String role, Instant expiresAt) {
        return token(accountId, role, "access", expiresAt, KEY_PAIR);
    }

    static String refreshToken(UUID accountId, Instant expiresAt) {
        return token(accountId, null, "refresh", expiresAt, KEY_PAIR);
    }

    /** 다른 키페어로 서명한 토큰 — 서명 위조 검증용. */
    static String tokenSignedWithOtherKey(UUID accountId, Instant expiresAt) {
        return token(accountId, "MEMBER", "access", expiresAt, OTHER_KEY_PAIR);
    }

    private static String token(UUID accountId, String role, String type, Instant expiresAt, KeyPair keyPair) {
        try {
            // iat은 반드시 exp보다 앞서야 한다 — 만료 토큰을 만들 때 iat을 now로 두면
            // Nimbus가 "만료"가 아니라 "형식 오류(expiresAt must be after issuedAt)"로 거부해서
            // 테스트가 검증하려던 만료 경로를 타지 않는다.
            var claims = new JWTClaimsSet.Builder()
                    .subject(accountId.toString())
                    .claim("typ", type)
                    .issueTime(Date.from(expiresAt.minusSeconds(1800)))
                    .expirationTime(Date.from(expiresAt));
            if (role != null) {
                claims.claim("role", role);
            }
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("테스트 토큰 생성 실패", e);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("테스트 RSA 키 생성 실패", e);
        }
    }
}
