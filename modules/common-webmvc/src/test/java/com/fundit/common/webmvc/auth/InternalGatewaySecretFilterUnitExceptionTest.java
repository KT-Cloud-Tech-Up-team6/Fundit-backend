package com.fundit.common.webmvc.auth;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InternalGatewaySecretFilterUnitExceptionTest {

    private static final String KEY = "test-only-internal-api-key";
    private static final List<InternalEndpoint> INTERNAL_ENDPOINTS =
            List.of(new InternalEndpoint("POST", "/api/v1/members"));

    private final InternalGatewaySecretFilter filter =
            new InternalGatewaySecretFilter(KEY, INTERNAL_ENDPOINTS, new ObjectMapper());

    @Test
    void 게이트웨이를_우회해_사용자_헤더만_위조하면_401을_반환한다() throws Exception {
        // given — 이 필터의 존재 이유. 서비스 포트로 직접 들어온 위조 요청이다.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
        request.addHeader(AuthHeaders.USER_ID, UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
        assertThat(chain.getRequest()).as("체인으로 넘어가면 안 된다").isNull();
    }

    @Test
    void 시크릿이_틀리면_401을_반환한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
        request.addHeader(AuthHeaders.USER_ID, UUID.randomUUID().toString());
        request.addHeader(AuthHeaders.INTERNAL_API_KEY, "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void 내부_전용_경로에_시크릿이_없으면_401을_반환한다() throws Exception {
        // given — 게이트웨이 라우팅 제외를 뚫고 직접 들어온 경우
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/members");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void 변수를_포함한_내부경로도_시크릿_없이는_막는다() throws Exception {
        // given — 경로를 문자열 equals로 비교하면 {accountId} 자리가 절대 매칭되지 않아
        // 내부 전용으로 선언해도 조용히 무방비가 된다
        var filter = new InternalGatewaySecretFilter(
                KEY,
                List.of(new InternalEndpoint("POST", "/api/v1/members/{accountId}/phone-verification")),
                new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/members/" + UUID.randomUUID() + "/phone-verification");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getHttpStatus());
    }
}
