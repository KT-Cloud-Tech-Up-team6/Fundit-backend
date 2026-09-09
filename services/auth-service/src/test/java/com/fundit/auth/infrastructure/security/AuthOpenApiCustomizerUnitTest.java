package com.fundit.auth.infrastructure.security;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.method.HandlerMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링 컨텍스트를 띄우지 않는다 — CI에 DB가 없고 이 레포에서 이미 깨진 지점이다.
 * 커스터마이저에 직접 OpenAPI 객체를 넣고 결과만 본다.
 */
class AuthOpenApiCustomizerUnitTest {

    private final AuthOpenApiCustomizer customizer = new AuthOpenApiCustomizer();

    @Test
    void 컨트롤러가_없는_로그인도_스펙에_들어간다() {
        // given — Security 필터가 처리해서 springdoc이 못 찾는 엔드포인트다
        OpenAPI openApi = new OpenAPI();

        // when
        customizer.loginEndpointCustomizer().customise(openApi);

        // then
        Operation login = openApi.getPaths().get("/api/v1/auth/login").getPost();
        assertThat(login).isNotNull();
        assertThat(login.getTags()).containsExactly("auth");
    }

    @Test
    void 로그인_스키마는_실제_요청_응답_레코드에서_뽑는다() {
        // given — 손으로 적으면 필드가 바뀌었을 때 조용히 어긋난다
        OpenAPI openApi = new OpenAPI();

        // when
        customizer.loginEndpointCustomizer().customise(openApi);

        // then
        Operation login = openApi.getPaths().get("/api/v1/auth/login").getPost();
        assertThat(schemaOf(login.getRequestBody().getContent()).getProperties())
                .containsOnlyKeys("email", "password");
        assertThat(schemaOf(login.getResponses().get("200").getContent()).getProperties())
                .containsOnlyKeys("accessToken", "mustChangePassword");
    }

    @Test
    void 로그인_성공_응답에_refreshToken_쿠키가_명시된다() {
        // given — 바디에만 있는 줄 알면 프론트가 재발급 흐름을 못 만든다
        OpenAPI openApi = new OpenAPI();

        // when
        customizer.loginEndpointCustomizer().customise(openApi);

        // then
        assertThat(openApi.getPaths().get("/api/v1/auth/login").getPost()
                .getResponses().get("200").getHeaders()).containsKey(HttpHeaders.SET_COOKIE);
    }

    @Test
    void AuthenticationPrincipal을_받는_오퍼레이션에만_인증_요구와_401이_붙는다() throws Exception {
        // given — 공통 설정은 @LoginUser만 보므로 이 서비스에선 아무 오퍼레이션도 잡히지 않는다
        Operation secured = new Operation().responses(new ApiResponses());
        Operation open = new Operation().responses(new ApiResponses());

        // when
        customizer.authenticationPrincipalCustomizer().customize(secured, handlerMethod("changePassword"));
        customizer.authenticationPrincipalCustomizer().customize(open, handlerMethod("checkEmail"));

        // then
        assertThat(secured.getSecurity()).isNotEmpty();
        assertThat(secured.getResponses().get("401")).isNotNull();
        assertThat(open.getSecurity()).isNull();
        assertThat(open.getResponses().get("401")).isNull();
    }

    private io.swagger.v3.oas.models.media.Schema<?> schemaOf(io.swagger.v3.oas.models.media.Content content) {
        return content.get("application/json").getSchema();
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        Class<?>[] params = methodName.equals("changePassword") ? new Class<?>[]{UUID.class} : new Class<?>[0];
        return new HandlerMethod(new TargetController(), TargetController.class.getMethod(methodName, params));
    }

    @SuppressWarnings("unused")
    static class TargetController {
        public void changePassword(@AuthenticationPrincipal UUID accountId) {
        }

        public void checkEmail() {
        }
    }
}
