package com.fundit.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * JWT 검증기. auth-service가 HS256 대칭키로 서명하므로 같은 {@code jwt.secret}을 공유한다
 * (사용자 확정 — RS256 + JWKS 전환은 별도 이슈).
 *
 * <p>대칭키가 두 서비스에 존재하는 게 이 방식의 유일한 단점이고, 그래서 검증기를
 * {@code JwtHeaderGlobalFilter}에서 분리해 이 클래스 하나로 격리해뒀다. RS256으로 넘어갈 때는
 * 이 빈의 본문만 {@code NimbusReactiveJwtDecoder.withJwkSetUri(...)}로 바꾸면 되고
 * 필터는 손대지 않는다.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(@Value("${jwt.secret}") String secret) {
        var secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
