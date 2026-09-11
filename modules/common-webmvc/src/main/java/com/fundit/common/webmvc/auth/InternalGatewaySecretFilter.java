package com.fundit.common.webmvc.auth;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 요청이 신뢰 경로(게이트웨이 또는 다른 내부 서비스)를 통해 들어왔는지 검증한다.
 *
 * <p><b>이 필터가 없으면 게이트웨이는 무의미하다.</b> 게이트웨이가 아무리 헤더를 정화해도,
 * 공격자가 게이트웨이를 건너뛰고 서비스 포트로 직접 {@code X-User-Id}를 위조해 보내면 그만이기
 * 때문이다. 공유 시크릿을 모르면 그 우회 경로가 막힌다.
 *
 * <p>검증이 필요한 조건(둘 중 하나라도 해당하면 필요):
 * <ul>
 *   <li>{@code X-User-Id} 헤더가 실려 있을 때 — 신원을 주장하는 요청이므로 출처를 확인해야 한다</li>
 *   <li>내부 전용 엔드포인트일 때 — 외부에 노출되면 안 되는 경로</li>
 * </ul>
 * 둘 다 아니면 통과시킨다(로그인·회원가입·약관조회처럼 로그인 전에 부르는 공개 API).
 */
public class InternalGatewaySecretFilter extends HttpFilter {

    private static final PathPatternParser PATH_PARSER = PathPatternParser.defaultInstance;

    private final String expectedApiKey;
    private final List<CompiledEndpoint> internalEndpoints;
    private final ObjectMapper objectMapper;

    public InternalGatewaySecretFilter(
            String expectedApiKey, List<InternalEndpoint> internalEndpoints, ObjectMapper objectMapper) {
        this.expectedApiKey = expectedApiKey;
        this.internalEndpoints = internalEndpoints.stream()
                .map(endpoint -> new CompiledEndpoint(endpoint.method(), PATH_PARSER.parse(endpoint.path())))
                .toList();
        this.objectMapper = objectMapper;
    }

    private record CompiledEndpoint(String method, PathPattern pattern) {
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (requiresSecret(request) && !hasValidSecret(request)) {
            response.setStatus(CommonErrorCode.UNAUTHORIZED.getHttpStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(CommonErrorCode.UNAUTHORIZED));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean requiresSecret(HttpServletRequest request) {
        return request.getHeader(AuthHeaders.USER_ID) != null || isInternalEndpoint(request);
    }

    /**
     * 게이트웨이(JwtHeaderGlobalFilter)와 같은 PathPattern 매칭을 쓴다. 문자열 equals로 비교하면
     * {@code /api/v1/members/{accountId}/phone-verification} 같은 변수 경로가 <b>절대 매칭되지 않아</b>
     * 내부 전용으로 선언해도 조용히 무방비가 된다. 두 판정이 같은 방식이어야 인코딩 우회로
     * 어긋나는 일도 없다(게이트웨이 쪽 주석 참고).
     */
    private boolean isInternalEndpoint(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return internalEndpoints.stream().anyMatch(endpoint ->
                endpoint.method().equalsIgnoreCase(request.getMethod())
                        && endpoint.pattern().matches(path));
    }

    /**
     * 상수 시간 비교 — 일반 equals는 앞부분부터 다른 위치에서 조기 반환하므로,
     * 응답 시간 차이로 키를 한 글자씩 알아낼 여지가 이론상 남는다.
     */
    private boolean hasValidSecret(HttpServletRequest request) {
        String provided = request.getHeader(AuthHeaders.INTERNAL_API_KEY);
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), expectedApiKey.getBytes(StandardCharsets.UTF_8));
    }
}
