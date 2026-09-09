package com.fundit.auth.infrastructure.security;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.List;

/**
 * auth-service에만 필요한 OpenAPI 보정. 둘 다 실기동으로 확인한 결함을 고치는 것이다.
 *
 * <ol>
 *   <li><b>로그인이 스펙에서 통째로 빠졌다.</b> {@link JsonLoginAuthenticationFilter}가 처리하는
 *       엔드포인트라 컨트롤러가 없고, springdoc은 핸들러 매핑만 훑으므로 찾지 못한다.
 *       프론트가 가장 먼저 쓰는 엔드포인트가 없는 스펙은 "구현 현황의 기준"일 수 없다.</li>
 *   <li><b>{@code PATCH /api/v1/auth/password}에 인증 표시가 없었다.</b> 공통 설정은
 *       {@code @LoginUser}(게이트웨이 헤더 방식)만 보는데, 이 서비스는 자체 JWT 필터를 쓰고
 *       컨트롤러는 {@code @AuthenticationPrincipal}로 받는다.</li>
 * </ol>
 *
 * <p>필터와 같은 패키지에 두는 이유: 로그인 스펙은 저 필터의 동작을 그대로 옮겨 적은 것이라,
 * 필터를 고치는 사람 눈에 같이 들어와야 한다.
 */
@Configuration
public class AuthOpenApiCustomizer {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";
    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String APPLICATION_JSON = "application/json";

    @Bean
    public GlobalOpenApiCustomizer loginEndpointCustomizer() {
        return openApi -> openApi.path(LOGIN_PATH, new PathItem().post(loginOperation()));
    }

    /** {@code @AuthenticationPrincipal}을 받는 오퍼레이션 = Access Token이 필요한 오퍼레이션. */
    @Bean
    public GlobalOperationCustomizer authenticationPrincipalCustomizer() {
        return (operation, handlerMethod) -> {
            if (requiresAccessToken(handlerMethod)) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
                operation.getResponses().addApiResponse("401", errorResponse("Access Token이 없거나 유효하지 않음"));
            }
            return operation;
        };
    }

    private boolean requiresAccessToken(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(AuthenticationPrincipal.class));
    }

    private Operation loginOperation() {
        return new Operation()
                .tags(List.of("auth"))
                .summary("로그인")
                .operationId("login")
                .description("Access Token은 응답 바디로, Refresh Token은 httpOnly 쿠키로 내려간다. "
                        + "실패 사유(자격증명 불일치/계정 잠금 등)는 오류 응답의 code로 구분한다.")
                .requestBody(new RequestBody().required(true)
                        .content(jsonContent(JsonLoginAuthenticationFilter.LoginRequestBody.class)))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("로그인 성공")
                                .content(jsonContent(LoginSuccessHandler.LoginResponseBody.class))
                                .addHeaderObject(HttpHeaders.SET_COOKIE, new Header()
                                        .description("refreshToken — HttpOnly; Secure; SameSite=Strict; "
                                                + "Path=/api/v1/auth/token/refresh")
                                        .schema(new StringSchema())))
                        .addApiResponse("default", errorResponse(
                                "오류 응답 — 형태와 공통 코드 목록은 ErrorResponse 스키마 참고")));
    }

    /** 스키마를 손으로 다시 적지 않고 실제 요청/응답 레코드에서 뽑는다 — 적어두면 언젠가 어긋난다. */
    private Content jsonContent(Class<?> type) {
        Schema<?> schema = ModelConverters.getInstance().readAllAsResolvedSchema(type).schema;
        return new Content().addMediaType(APPLICATION_JSON,
                new MediaType().schema(schema));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(APPLICATION_JSON,
                        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF))));
    }
}
