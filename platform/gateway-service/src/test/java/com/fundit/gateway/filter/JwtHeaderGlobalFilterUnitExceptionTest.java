package com.fundit.gateway.filter;

import com.fundit.common.auth.AuthHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtHeaderGlobalFilterUnitExceptionTest {

    private static final String INTERNAL_API_KEY = "test-only-internal-api-key";

    private JwtHeaderGlobalFilter filter;
    private RecordingChain chain;

    @BeforeEach
    void setUp() {
        filter = GatewayFilterFixture.filter(INTERNAL_API_KEY);
        chain = new RecordingChain();
    }

    @Test
    void 클라이언트가_보낸_사용자_헤더는_제거하고_내부키는_덮어쓴다() {
        // given — 이 필터의 존재 이유. 토큰 없이 헤더만 위조해 보낸 요청이다.
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/members/me")
                        .header(AuthHeaders.USER_ID, UUID.randomUUID().toString())
                        .header(AuthHeaders.USER_ROLES, "ADMIN")
                        .header(AuthHeaders.INTERNAL_API_KEY, "attacker-guessed-key"));

        // when
        filter.filter(exchange, chain).block();

        // then — 위조된 신원은 사라지고, 내부키는 게이트웨이 값으로 대체된다
        ServerHttpRequest forwarded = chain.forwardedRequest();
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.USER_ID)).isNull();
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.USER_ROLES)).isNull();
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.INTERNAL_API_KEY)).isEqualTo(INTERNAL_API_KEY);
    }

    @Test
    void 유효한_토큰과_함께_보낸_위조_사용자_헤더도_토큰_기준으로_덮어쓴다() {
        // given — 토큰은 본인 것이지만 헤더로 다른 사람인 척 하는 시도
        UUID realAccountId = UUID.randomUUID();
        UUID spoofedAccountId = UUID.randomUUID();
        String token = GatewayFilterFixture.accessToken(realAccountId, "MEMBER", Instant.now().plusSeconds(600));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + token)
                        .header(AuthHeaders.USER_ID, spoofedAccountId.toString())
                        .header(AuthHeaders.USER_ROLES, "ADMIN"));

        // when
        filter.filter(exchange, chain).block();

        // then
        ServerHttpRequest forwarded = chain.forwardedRequest();
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.USER_ID)).isEqualTo(realAccountId.toString());
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.USER_ROLES)).isEqualTo("MEMBER");
    }

    @Test
    void 내부_전용_엔드포인트는_게이트웨이_경유로_접근하면_404를_반환한다() {
        // given — 게이트웨이가 모든 요청에 내부키를 주입하므로, 이 차단이 없으면 그냥 뚫린다
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/members").header("Content-Type", "application/json"));

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chain.reached()).isFalse();
    }

    @Test
    void 내부_전용_엔드포인트_차단은_GET_회원조회에는_영향을_주지_않는다() {
        // given — 차단 대상은 POST + 정확히 /api/v1/members 조합뿐이다
        String token = GatewayFilterFixture.accessToken(
                UUID.randomUUID(), "MEMBER", Instant.now().plusSeconds(600));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/members/me").header("Authorization", "Bearer " + token));

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(chain.reached()).isTrue();
    }

    @Test
    void 서명이_위조된_토큰이면_401_TOKEN_INVALID를_반환한다() {
        // given
        String forged = GatewayFilterFixture.tokenSignedWith(
                "another-secret-key-at-least-32-bytes-long!", UUID.randomUUID(), Instant.now().plusSeconds(600));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/members/me").header("Authorization", "Bearer " + forged));

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseBody(exchange)).contains("TOKEN_INVALID");
        assertThat(chain.reached()).isFalse();
    }

    @Test
    void 만료된_토큰이면_401_TOKEN_EXPIRED를_반환한다() {
        // given
        String expired = GatewayFilterFixture.accessToken(
                UUID.randomUUID(), "MEMBER", Instant.now().minusSeconds(600));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/members/me").header("Authorization", "Bearer " + expired));

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseBody(exchange)).contains("TOKEN_EXPIRED");
        assertThat(chain.reached()).isFalse();
    }

    @Test
    void refresh_토큰으로_API를_호출하면_401_TOKEN_INVALID를_반환한다() {
        // given — refresh 토큰은 재발급 전용이라 일반 API 호출에 쓰이면 안 된다
        String refresh = GatewayFilterFixture.refreshToken(UUID.randomUUID(), Instant.now().plusSeconds(600));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/members/me").header("Authorization", "Bearer " + refresh));

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(responseBody(exchange)).contains("TOKEN_INVALID");
        assertThat(chain.reached()).isFalse();
    }

    private String responseBody(ServerWebExchange exchange) {
        return ((MockServerWebExchange) exchange).getResponse()
                .getBodyAsString().block();
    }

    private static final class RecordingChain implements GatewayFilterChain {

        private ServerWebExchange received;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.received = exchange;
            return Mono.empty();
        }

        boolean reached() {
            return received != null;
        }

        ServerHttpRequest forwardedRequest() {
            assertThat(received).as("체인까지 도달하지 못했다 — 필터가 요청을 끊었다").isNotNull();
            return received.getRequest();
        }
    }
}
