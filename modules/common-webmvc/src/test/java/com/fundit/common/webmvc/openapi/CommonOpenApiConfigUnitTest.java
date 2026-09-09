package com.fundit.common.webmvc.openapi;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.InternalEndpoint;
import com.fundit.common.webmvc.auth.LoginUser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링 컨텍스트를 띄우지 않는다 — CI에 DB가 없고 이 레포에서 이미 두 번 깨진 지점이다.
 * 커스터마이저에 직접 OpenAPI 객체를 넣고 결과만 본다.
 */
class CommonOpenApiConfigUnitTest {

    private final CommonOpenApiConfig config = new CommonOpenApiConfig();

    @Test
    void 모든_오퍼레이션에_공통_에러_응답이_붙는다() throws Exception {
        // given
        Operation operation = new Operation().responses(new ApiResponses());

        // when
        config.commonResponseCustomizer().customize(operation, handlerMethod("publicEndpoint"));

        // then
        assertThat(operation.getResponses().get("default")).isNotNull();
        assertThat(operation.getResponses().get("default").getContent().get("application/json")
                .getSchema().get$ref()).endsWith("/ErrorResponse");
    }

    @Test
    void LoginUser를_받는_오퍼레이션에만_인증_요구와_401이_붙는다() throws Exception {
        // given
        Operation secured = new Operation().responses(new ApiResponses());
        Operation open = new Operation().responses(new ApiResponses());

        // when
        config.commonResponseCustomizer().customize(secured, handlerMethod("securedEndpoint"));
        config.commonResponseCustomizer().customize(open, handlerMethod("publicEndpoint"));

        // then
        assertThat(secured.getSecurity()).isNotEmpty();
        assertThat(secured.getResponses().get("401")).isNotNull();
        assertThat(open.getSecurity()).isNull();
        assertThat(open.getResponses().get("401")).isNull();
    }

    @Test
    void 서비스가_선언한_내부_전용_엔드포인트는_스펙에서_제거된다() {
        // given — 게이트웨이가 404로 막는 경로라 스펙에 남으면 항상 실패하는 클라이언트가 생성된다
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/members", new PathItem()
                        .post(new Operation())
                        .get(new Operation()))
                .addPathItem("/internal-only", new PathItem().post(new Operation())));

        // when
        config.internalEndpointHidingCustomizer(provider(
                new InternalEndpoint("POST", "/api/v1/members"),
                new InternalEndpoint("POST", "/internal-only"))).customise(openApi);

        // then — POST만 지우고 같은 경로의 GET은 남긴다
        assertThat(openApi.getPaths().get("/api/v1/members").getPost()).isNull();
        assertThat(openApi.getPaths().get("/api/v1/members").getGet()).isNotNull();
        // 오퍼레이션이 하나도 안 남으면 경로 자체를 지운다
        assertThat(openApi.getPaths()).doesNotContainKey("/internal-only");
    }

    @Test
    void 자동_생성된_servers는_제거된다() {
        // given — springdoc 기본값은 서비스의 직접 포트라 게이트웨이를 통해 부르는 쪽엔 거짓 정보다
        OpenAPI openApi = new OpenAPI();
        openApi.addServersItem(new io.swagger.v3.oas.models.servers.Server().url("http://localhost:8082"));

        // when
        config.commonSchemaCustomizer("member-service").customise(openApi);

        // then
        assertThat(openApi.getServers()).isNull();
        assertThat(openApi.getInfo().getTitle()).isEqualTo("member-service API");
        assertThat(openApi.getComponents().getSchemas()).containsKey("ErrorResponse");
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    }

    @Test
    void 내부_전용_엔드포인트를_선언하지_않은_서비스에서도_동작한다() {
        // given — InternalEndpoint 빈을 하나도 두지 않는 서비스가 있다(auth-service).
        // List로 주입받으면 후보 빈이 없을 때 컨텍스트 기동 자체가 실패한다.
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/auth/login", new PathItem().post(new Operation())));

        // when
        config.internalEndpointHidingCustomizer(provider()).customise(openApi);

        // then — 아무것도 지우지 않고 그대로 통과해야 한다
        assertThat(openApi.getPaths()).containsKey("/api/v1/auth/login");
    }

    /** ObjectProvider는 인터페이스라 테스트에선 stream()만 채워 쓴다. */
    private ObjectProvider<InternalEndpoint> provider(InternalEndpoint... endpoints) {
        return new ObjectProvider<>() {
            @Override
            public Stream<InternalEndpoint> stream() {
                return Stream.of(endpoints);
            }

            @Override
            public InternalEndpoint getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public InternalEndpoint getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public InternalEndpoint getIfAvailable() {
                return null;
            }

            @Override
            public InternalEndpoint getIfUnique() {
                return null;
            }
        };
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        Class<?>[] params = methodName.equals("securedEndpoint") ? new Class<?>[]{CurrentUser.class} : new Class<?>[0];
        return new HandlerMethod(new TargetController(), TargetController.class.getMethod(methodName, params));
    }

    @SuppressWarnings("unused")
    static class TargetController {
        public void securedEndpoint(@LoginUser CurrentUser user) {
        }

        public void publicEndpoint() {
        }
    }
}
