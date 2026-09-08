package com.fundit.gateway.filter;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 게이트웨이 필터 테스트용 픽스처. auth-service가 실제로 발급하는 것과 같은 형태의
 * HS256 토큰(sub/role/typ 클레임)을 만들어, 목이 아니라 진짜 서명 검증을 거치게 한다.
 */
final class GatewayFilterFixture {

    /** JwtProperties가 최소 32바이트를 요구하므로 테스트 키도 같은 길이로 맞춘다. */
    static final String SECRET = "test-only-secret-key-at-least-32-bytes-long!!";

    private GatewayFilterFixture() {
    }

    static JwtHeaderGlobalFilter filter(String internalApiKey) {
        return new JwtHeaderGlobalFilter(decoder(), new ObjectMapper(), internalApiKey);
    }

    static ReactiveJwtDecoder decoder() {
        var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    static String accessToken(UUID accountId, String role, Instant expiresAt) {
        return token(accountId, role, "access", expiresAt, SECRET);
    }

    static String refreshToken(UUID accountId, Instant expiresAt) {
        return token(accountId, null, "refresh", expiresAt, SECRET);
    }

    /** 서명 키가 다른 토큰 — 위조 서명 검증용. */
    static String tokenSignedWith(String otherSecret, UUID accountId, Instant expiresAt) {
        return token(accountId, "MEMBER", "access", expiresAt, otherSecret);
    }

    private static String token(UUID accountId, String role, String type, Instant expiresAt, String secret) {
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
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("테스트 토큰 생성 실패", e);
        }
    }
}
