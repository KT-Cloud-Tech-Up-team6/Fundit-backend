package com.fundit.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * JWT 검증기. auth-service가 RS256으로 서명하고, 여기서는 그 JWKS 엔드포인트에서
 * <b>공개키만</b> 가져와 검증한다 — 개인키는 auth-service 밖으로 나가지 않는다.
 *
 * <p>Nimbus가 가져온 공개키를 캐싱하므로 정상 운영 중 auth-service가 잠시 죽어도 검증은 계속된다.
 * 다만 게이트웨이가 재기동됐는데 auth-service가 죽어 있으면 캐시가 비어 전체 401이 된다 —
 * 알려진 한계이고, 기동 시 프리페치/재시도 대신 K8s readiness probe로 다룰 문제로 본다.
 */
@Configuration
public class JwtDecoderConfig {

    /** MemberServiceRestClient와 같은 값. 새 숫자를 만들지 않는다(CLAUDE.md: 동기 호출 타임아웃 필수). */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(@Value("${jwt.jwk-set-uri}") String jwkSetUri) {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .webClient(timeoutBoundWebClient())
                .build();
    }

    /**
     * 리액티브 빌더에는 서블릿 쪽 {@code RestTemplateWithDefaultTimeouts} 같은 기본 타임아웃이 없다 —
     * 직접 주지 않으면 JWKS 조회가 무한정 매달린다.
     */
    private WebClient timeoutBoundWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .responseTimeout(TIMEOUT)
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(TIMEOUT.toSeconds(), TimeUnit.SECONDS)));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }
}
