package com.fundit.gateway.filter;

import com.fundit.common.auth.AuthHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요청이 다운스트림까지 통과하는 흐름을 검증한다. 401/404로 끊기는 경우는
 * JwtHeaderGlobalFilterUnitExceptionTest에 있다(test-convention.md의 정상/예외 파일 분리).
 *
 * <p>스프링 컨텍스트를 띄우지 않는 순수 단위 테스트다 — application-local.yml이 .gitignore 대상이라
 * CI 체크아웃 트리엔 없어서, @SpringBootTest를 쓰면 jwt.secret/internal-api.key가 미해석 상태로
 * PlaceholderResolutionException이 난다(auth-service에서 실제로 겪은 실패).
 */
class JwtHeaderGlobalFilterUnitTest {

    private static final String INTERNAL_API_KEY = "test-only-internal-api-key";

    private JwtHeaderGlobalFilter filter;
    private RecordingChain chain;

    @BeforeEach
    void setUp() {
        filter = GatewayFilterFixture.filter(INTERNAL_API_KEY);
        chain = new RecordingChain();
    }

    @Test
    void 유효한_액세스토큰이면_사용자_헤더와_내부키를_주입해_통과시킨다() {
        // given
        UUID accountId = UUID.randomUUID();
        String token = GatewayFilterFixture.accessToken(accountId, "MEMBER", Instant.now().plusSeconds(600));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/members/me").header("Authorization", "Bearer " + token));

        // when
        filter.filter(exchange, chain).block();

        // then
        ServerHttpRequest forwarded = chain.forwardedRequest();
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.USER_ID)).isEqualTo(accountId.toString());
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.USER_ROLES)).isEqualTo("MEMBER");
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.INTERNAL_API_KEY)).isEqualTo(INTERNAL_API_KEY);
    }

    @Test
    void 토큰이_없으면_사용자_헤더_없이_내부키만_붙여_통과시킨다() {
        // given — 인증이 필요한 요청인지는 다운스트림이 판단한다
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login"));

        // when
        filter.filter(exchange, chain).block();

        // then
        ServerHttpRequest forwarded = chain.forwardedRequest();
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.USER_ID)).isNull();
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.USER_ROLES)).isNull();
        assertThat(forwarded.getHeaders().getFirst(AuthHeaders.INTERNAL_API_KEY)).isEqualTo(INTERNAL_API_KEY);
    }

    @Test
    void Authorization_헤더는_제거하지_않고_그대로_전달한다() {
        // given — auth-service는 토큰 발급자로서 자체 검증을 한 번 더 한다
        String token = GatewayFilterFixture.accessToken(
                UUID.randomUUID(), "MEMBER", Instant.now().plusSeconds(600));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.patch("/api/v1/auth/password").header("Authorization", "Bearer " + token));

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(chain.forwardedRequest().getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer " + token);
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
    void 내부_전용_엔드포인트_차단은_GET_회원조회에는_영향을_주지_않는다() {
        // given — 차단 대상은 POST + 정확히 그 경로 조합뿐이다
        String token = GatewayFilterFixture.accessToken(
                UUID.randomUUID(), "MEMBER", Instant.now().plusSeconds(600));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/members/me").header("Authorization", "Bearer " + token));

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(chain.reached()).isTrue();
    }

    /** 체인이 실제로 받은(=다운스트림으로 나갈) 요청을 붙잡아두는 테스트용 체인. */
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
