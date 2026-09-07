package com.fundit.gateway.filter;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.ErrorCode;
import com.fundit.common.error.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;

/**
 * 게이트웨이의 인증 처리 전부. 순서가 곧 보안 계약이다.
 *
 * <ol>
 *   <li>클라이언트가 보낸 신뢰 헤더를 무조건 제거한다 — 이 단계가 없으면 게이트웨이를 세워도
 *       {@code X-User-Id} 위조가 그대로 통한다.</li>
 *   <li>내부 전용 엔드포인트({@code POST /api/v1/members})는 즉시 404로 막는다. 아래 3번에서
 *       게이트웨이가 <b>모든</b> 프록시 요청에 내부 키를 주입하기 때문에, 이 차단이 없으면
 *       외부 클라이언트가 게이트웨이를 통해 내부 키 검증을 그냥 통과해버린다.</li>
 *   <li>내부 키를 주입한다(게이트웨이를 거쳤다는 증명).</li>
 *   <li>토큰이 없으면 사용자 헤더 없이 통과시킨다 — 인증이 필요한지는 다운스트림이 판단한다.
 *       게이트웨이가 "인증 필요 경로 목록"을 중복으로 들고 있으면 서비스와 어긋나기 시작한다.</li>
 *   <li>토큰이 있으면 서명을 검증하고 {@code sub}/{@code role}을 헤더로 옮긴다.
 *       무효·만료면 여기서 401로 끊는다.</li>
 * </ol>
 *
 * <p>{@code Authorization} 헤더는 제거하지 않고 그대로 전달한다 — auth-service의
 * {@code PATCH /api/v1/auth/password}는 토큰 발급자 본인으로서 자체 Spring Security 필터로
 * 다시 검증한다.
 */
@Component
public class JwtHeaderGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TYPE_CLAIM = "typ";
    private static final String ACCESS_TYPE = "access";
    private static final String ROLE_CLAIM = "role";

    /**
     * 게이트웨이 라우팅에서 제외해야 하는 내부 전용 엔드포인트.
     * 라우트 predicate가 아니라 여기서 막는 이유: Spring Cloud Gateway에 "즉시 404 응답" 필터가 없고,
     * {@code /api/v1/members/**} 패턴이 {@code /api/v1/members}까지 매칭하기 때문에
     * 라우트 정의만으로는 이 한 조합(POST + 정확히 이 경로)을 떼어낼 수 없다.
     * 라우트 predicate와 같은 PathPattern 매칭을 써서 인코딩 우회(예: {@code /api/v1/me%6dbers})로
     * 두 판정이 어긋나는 일이 없게 한다.
     */
    private static final PathPattern INTERNAL_MEMBER_CREATE_PATH =
            new PathPatternParser().parse("/api/v1/members");

    private final ReactiveJwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;
    private final String internalApiKey;

    public JwtHeaderGlobalFilter(
            ReactiveJwtDecoder jwtDecoder,
            ObjectMapper objectMapper,
            @Value("${internal-api.key}") String internalApiKey) {
        this.jwtDecoder = jwtDecoder;
        this.objectMapper = objectMapper;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (isInternalOnlyEndpoint(request)) {
            return writeError(exchange, CommonErrorCode.NOT_FOUND);
        }

        String token = bearerToken(request);
        if (token == null) {
            return chain.filter(withTrustedHeaders(exchange, sanitize(request).build()));
        }

        return jwtDecoder.decode(token)
                .flatMap(jwt -> forwardAuthenticated(exchange, request, jwt, chain))
                .onErrorResume(JwtException.class, e -> writeError(exchange, toErrorCode(e)));
    }

    private Mono<Void> forwardAuthenticated(
            ServerWebExchange exchange, ServerHttpRequest request, Jwt jwt, GatewayFilterChain chain) {

        // access 토큰이 아닌 걸(=refresh 토큰) API 호출에 쓰지 못하게 막는다.
        // auth-service JwtTokenProvider.requireType()과 같은 이유 — 두 토큰의 수명·용도가 다르다.
        if (!ACCESS_TYPE.equals(jwt.getClaimAsString(TYPE_CLAIM))) {
            return writeError(exchange, CommonErrorCode.TOKEN_INVALID);
        }

        ServerHttpRequest mutated = sanitize(request)
                .header(AuthHeaders.USER_ID, jwt.getSubject())
                .header(AuthHeaders.USER_ROLES, jwt.getClaimAsString(ROLE_CLAIM))
                .build();
        return chain.filter(withTrustedHeaders(exchange, mutated));
    }

    /**
     * 클라이언트가 직접 보낸 신뢰 헤더를 버리고, 게이트웨이가 발급한 내부 키로 덮어쓴다.
     * 인증된 요청이면 호출부가 사용자 헤더를 다시 채운다.
     */
    private ServerHttpRequest.Builder sanitize(ServerHttpRequest request) {
        return request.mutate().headers(headers -> {
            headers.remove(AuthHeaders.USER_ID);
            headers.remove(AuthHeaders.USER_ROLES);
            headers.set(AuthHeaders.INTERNAL_API_KEY, internalApiKey);
        });
    }

    private ServerWebExchange withTrustedHeaders(ServerWebExchange exchange, ServerHttpRequest request) {
        return exchange.mutate().request(request).build();
    }

    private boolean isInternalOnlyEndpoint(ServerHttpRequest request) {
        return HttpMethod.POST.equals(request.getMethod())
                && INTERNAL_MEMBER_CREATE_PATH.matches(request.getPath().pathWithinApplication());
    }

    private String bearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * ponytail: Spring이 만료와 서명오류를 같은 예외 계층으로 던져서 메시지 문자열로 구분한다.
     * Spring 버전이 올라가 문구가 바뀌면 만료도 TOKEN_INVALID로 떨어질 뿐 인증이 뚫리지는 않는다
     * (실패 방향이 안전한 쪽). 이 구분이 중요해지면 exp 클레임을 직접 파싱하는 방식으로 교체할 것.
     */
    private ErrorCode toErrorCode(JwtException e) {
        String message = e.getMessage();
        boolean expired = message != null && message.toLowerCase(Locale.ROOT).contains("expired");
        return expired ? CommonErrorCode.TOKEN_EXPIRED : CommonErrorCode.TOKEN_INVALID;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, ErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(errorCode.getHttpStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = objectMapper.writeValueAsBytes(ErrorResponse.of(errorCode));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    /**
     * 라우팅(NettyRoutingFilter)보다 먼저 실행되어야 헤더 정화·주입이 실제 전송에 반영된다.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
