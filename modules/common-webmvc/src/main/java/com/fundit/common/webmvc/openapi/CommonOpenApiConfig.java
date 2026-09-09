package com.fundit.common.webmvc.openapi;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.ErrorResponse;
import com.fundit.common.webmvc.auth.InternalEndpoint;
import com.fundit.common.webmvc.auth.LoginUser;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 모든 Servlet 서비스가 공유하는 OpenAPI 보정. 서비스마다 따로 문서화하면 또 어긋나므로 여기서 한 번만 정의한다.
 *
 * <p>둘 다 실기동으로 확인한 실제 결함을 고치는 것이지, 예방적으로 넣은 게 아니다:
 * <ol>
 *   <li>{@code @LoginUser CurrentUser}가 <b>쿼리 파라미터로</b> 문서화됐다 —
 *       springdoc은 우리 {@code LoginUserArgumentResolver}를 모른다. 그대로 두면 스펙이
 *       "{@code ?user=...}를 보내라"고 거짓말한다.</li>
 *   <li>에러 응답이 하나도 잡히지 않았다 — {@code @RestControllerAdvice}는 서비스의 빈 자식 클래스에 붙어 있고
 *       실제 {@code @ExceptionHandler}는 부모({@code AbstractGlobalExceptionHandler})에 있어서
 *       springdoc이 상속받은 핸들러를 보지 못한다.</li>
 * </ol>
 */
@Configuration
public class CommonOpenApiConfig {

    private static final String ERROR_SCHEMA = "ErrorResponse";
    private static final String BEARER_SCHEME = "bearerAuth";

    static {
        // @LoginUser는 게이트웨이가 넣는 X-User-Id 헤더에서 LoginUserArgumentResolver가 조립한다.
        // springdoc에 알려주지 않으면 일반 파라미터로 보고 쿼리 스트링으로 문서화한다.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUser.class);
    }

    /** 에러 응답 스키마와 인증 방식을 한 번만 등록한다. */
    @Bean
    public GlobalOpenApiCustomizer commonSchemaCustomizer(
            @Value("${spring.application.name}") String applicationName) {
        return openApi -> {
            openApi.setInfo(new Info().title(applicationName + " API").version("v1"));

            // springdoc이 자동으로 넣는 servers는 이 서비스의 직접 포트(예: localhost:8082)라
            // 게이트웨이를 통해 호출하는 클라이언트에게는 거짓 정보다. 상대 URL "/"는 OpenAPI 규약상
            // "스펙을 받아온 곳과 같은 origin"을 뜻해서, 어디서 받아가든 맞는 값이 된다.
            //
            // 비우지(setServers(null)) 않는 이유: springdoc은 캐시된 OpenAPI에 매 요청 기본 서버를
            // 다시 채워 넣는다. 그래서 부팅 후 첫 요청에만 servers가 없고 두 번째부터 되살아난다
            // — 실기동으로 확인했다. 비어 있지 않으면 건드리지 않으므로 값을 넣어 고정한다.
            openApi.setServers(List.of(new Server().url("/").description("스펙을 받아온 곳과 같은 origin")));

            Components components = openApi.getComponents() != null ? openApi.getComponents() : new Components();

            components.addSchemas(ERROR_SCHEMA, errorResponseSchema());
            components.addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("게이트웨이가 이 토큰을 검증해 " + AuthHeaders.USER_ID + " 헤더로 변환한다. "
                            + "클라이언트가 그 헤더를 직접 보내면 게이트웨이가 제거한다."));

            openApi.setComponents(components);
        };
    }

    /**
     * 내부 전용 엔드포인트를 스펙에서 제거한다. 게이트웨이가 404로 막는 경로라, 남겨두면 프론트가
     * 클라이언트를 생성했을 때 항상 실패하는 메서드가 생긴다.
     *
     * <p>경로 목록을 여기에 다시 적지 않고 서비스가 이미 선언한 {@link InternalEndpoint} 빈을 그대로 읽는다 —
     * 목록이 두 곳에 생기면 언젠가 어긋난다.
     *
     * <p>{@code List}가 아니라 {@code ObjectProvider}로 받는다 — 내부 전용 엔드포인트가 없는 서비스도 있고,
     * 필수 컬렉션 주입은 후보 빈이 하나도 없으면 컨텍스트 기동이 실패한다. {@code CommonWebConfig}와 같은 이유.
     */
    @Bean
    public GlobalOpenApiCustomizer internalEndpointHidingCustomizer(ObjectProvider<InternalEndpoint> internalEndpoints) {
        return openApi -> internalEndpoints.forEach(endpoint -> {
            PathItem pathItem = openApi.getPaths() == null ? null : openApi.getPaths().get(endpoint.path());
            if (pathItem != null) {
                pathItem.operation(PathItem.HttpMethod.valueOf(endpoint.method().toUpperCase()), null);
                if (pathItem.readOperations().isEmpty()) {
                    openApi.getPaths().remove(endpoint.path());
                }
            }
        });
    }

    /**
     * 모든 오퍼레이션에 공통 에러 응답을 붙이고, 로그인이 필요한 오퍼레이션에 인증 요구를 표시한다.
     * 상태코드별로 일일이 나열하는 대신 OpenAPI의 {@code default} 응답을 쓴다 — "명시되지 않은 모든 응답"이라는
     * 뜻이라 의미가 정확하고, 엔드포인트마다 어떤 에러가 나는지 손으로 관리하지 않아도 된다.
     */
    @Bean
    public GlobalOperationCustomizer commonResponseCustomizer() {
        return (operation, handlerMethod) -> {
            operation.getResponses().addApiResponse("default", errorApiResponse());
            if (requiresLogin(handlerMethod)) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
                operation.getResponses().addApiResponse("401", unauthorizedResponse());
            }
            return operation;
        };
    }

    private boolean requiresLogin(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(LoginUser.class));
    }

    private ApiResponse errorApiResponse() {
        return errorResponseWithDescription("오류 응답 — 형태와 공통 코드 목록은 ErrorResponse 스키마 참고");
    }

    private ApiResponse unauthorizedResponse() {
        return errorResponseWithDescription("인증 필요 — 유효한 Access Token 없이 호출한 경우("
                + CommonErrorCode.UNAUTHORIZED.getCode() + ")");
    }

    private ApiResponse errorResponseWithDescription(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
    }

    private String commonErrorCodes() {
        return Arrays.stream(CommonErrorCode.values())
                .map(code -> code.getCode() + "(" + code.getHttpStatus() + ")")
                .collect(Collectors.joining(", "));
    }

    /** ErrorResponse는 컨트롤러 시그니처에 안 나타나서 자동 수집되지 않는다 — 직접 변환해 등록한다. */
    private Schema<?> errorResponseSchema() {
        return ModelConverters.getInstance()
                .readAllAsResolvedSchema(ErrorResponse.class)
                .schema
                .description("모든 서비스가 공유하는 에러 응답 형태. detail은 오류 종류에 따라 모양이 다르다 "
                        + "(검증 실패는 [{field, reason}] 배열, 그 외는 대부분 null). "
                        + "공통 코드: " + commonErrorCodes() + ". 서비스별 코드는 각 서비스의 ErrorCode enum 참고.");
    }
}
