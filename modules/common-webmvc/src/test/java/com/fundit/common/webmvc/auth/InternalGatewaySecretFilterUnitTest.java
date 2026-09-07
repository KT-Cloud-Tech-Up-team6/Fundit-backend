package com.fundit.common.webmvc.auth;

import com.fundit.common.auth.AuthHeaders;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InternalGatewaySecretFilterUnitTest {

    private static final String KEY = "test-only-internal-api-key";
    private static final List<InternalEndpoint> INTERNAL_ENDPOINTS =
            List.of(new InternalEndpoint("POST", "/api/v1/members"));

    private final InternalGatewaySecretFilter filter =
            new InternalGatewaySecretFilter(KEY, INTERNAL_ENDPOINTS, new ObjectMapper());

    @Test
    void 사용자_헤더도_내부경로도_아니면_시크릿_없이_통과시킨다() throws Exception {
        // given — 로그인·회원가입처럼 로그인 전에 부르는 공개 API
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 사용자_헤더가_있고_시크릿이_맞으면_통과시킨다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
        request.addHeader(AuthHeaders.USER_ID, UUID.randomUUID().toString());
        request.addHeader(AuthHeaders.INTERNAL_API_KEY, KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 내부_전용_경로에_시크릿이_맞으면_통과시킨다() throws Exception {
        // given — auth-service가 회원가입 중 호출하는 경로
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/members");
        request.addHeader(AuthHeaders.INTERNAL_API_KEY, KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 내부_전용_경로와_메서드가_다르면_시크릿을_요구하지_않는다() throws Exception {
        // given — GET /api/v1/members는 내부 전용 목록(POST)에 해당하지 않는다
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
