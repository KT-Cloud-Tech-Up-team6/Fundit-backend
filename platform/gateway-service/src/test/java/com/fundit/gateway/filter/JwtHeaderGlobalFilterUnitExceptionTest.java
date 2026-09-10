package com.fundit.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 필터가 요청을 끊고 401/404를 직접 응답하는 경우만 모아둔다.
 * 다운스트림까지 통과하는 흐름(위조 헤더 제거 포함)은 JwtHeaderGlobalFilterUnitTest에 있다.
 */
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
    void 내부_전용_회원생성_엔드포인트는_게이트웨이_경유로_접근하면_404를_반환한다() {
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
    void 경로_변수가_있는_내부_전용_엔드포인트도_404를_반환한다() {
        // given — 소셜 계정 연동의 본인 확인. accountId가 경로 변수라 문자열 비교로는 잡히지 않는다
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/members/" + UUID.randomUUID() + "/phone-verification")
                        .header("Content-Type", "application/json"));

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chain.reached()).isFalse();
    }

    @Test
    void 서명이_위조된_토큰이면_401_TOKEN_INVALID를_반환한다() {
        // given
        String forged = GatewayFilterFixture.tokenSignedWithOtherKey(
                UUID.randomUUID(), Instant.now().plusSeconds(600));
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
        return ((MockServerWebExchange) exchange).getResponse().getBodyAsString().block();
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
    }
}
