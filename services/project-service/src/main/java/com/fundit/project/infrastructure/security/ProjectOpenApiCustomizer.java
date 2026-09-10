package com.fundit.project.infrastructure.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;

/**
 * project-service에만 필요한 OpenAPI 보정. 실기동으로 확인한 결함을 고치는 것이다:
 * {@code @CurrentMember}/{@code @CurrentAdmin}은 {@code CommonOpenApiConfig}가 아는
 * {@code @LoginUser}가 아니라서, 그대로 두면 springdoc이 이 UUID 파라미터를
 * "쿼리로 sellerId를 보내라"는 식으로 잘못 문서화한다 — 실제로는
 * {@link CurrentMemberArgumentResolver}/{@link CurrentAdminArgumentResolver}가
 * {@value CurrentMemberArgumentResolver#ACCOUNT_ID_HEADER}(관리자는
 * {@value CurrentAdminArgumentResolver#ACCOUNT_ROLE_HEADER}도) 헤더에서 채운다.
 *
 * <p>{@code bearerAuth}가 아니라 별도 보안 스킴을 쓰는 이유: 이 서비스는 아직 게이트웨이 라우트가
 * 없어 JWT를 검증하지 않고 헤더를 그대로 신뢰하는 임시 상태다(각 리졸버의 "ponytail" 주석 참고).
 * 게이트웨이가 붙으면 이 스킴도 bearerAuth로 교체해야 한다 — 그때까지 문서가 실제 동작과
 * 다르게 "JWT를 보내라"고 말하지 않도록 별도로 둔다.
 */
@Configuration
public class ProjectOpenApiCustomizer {

    private static final String ACCOUNT_HEADER_SCHEME = "accountIdHeader";
    private static final String ACCOUNT_ROLE_HEADER_SCHEME = "accountRoleHeader";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";
    private static final String APPLICATION_JSON = "application/json";

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentMember.class, CurrentAdmin.class);
    }

    @Bean
    public GlobalOpenApiCustomizer accountHeaderSchemeCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents() != null ? openApi.getComponents() : new Components();
            components.addSecuritySchemes(ACCOUNT_HEADER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name(CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER)
                    .description("게이트웨이 라우트가 아직 없어 서명 검증 없이 이 헤더를 그대로 신뢰한다(로컬 직접 접속 전용)."));
            // 관리자 전용 엔드포인트는 이 헤더도 같이 요구한다 — 별도 스킴으로 선언해야
            // "함께 필요하다"는 게 설명 문구가 아니라 스펙(SecurityRequirement)에 기계적으로 드러난다.
            components.addSecuritySchemes(ACCOUNT_ROLE_HEADER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name(CurrentAdminArgumentResolver.ACCOUNT_ROLE_HEADER)
                    .description("관리자 전용 엔드포인트에서만 요구된다. 값이 admin이 아니면 403."));
            openApi.setComponents(components);
        };
    }

    @Bean
    public GlobalOperationCustomizer accountHeaderRequirementCustomizer() {
        return (operation, handlerMethod) -> {
            if (requiresAdmin(handlerMethod)) {
                operation.addSecurityItem(new SecurityRequirement()
                        .addList(ACCOUNT_HEADER_SCHEME)
                        .addList(ACCOUNT_ROLE_HEADER_SCHEME));
                operation.getResponses().addApiResponse("401", errorResponse(
                        CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER + " 헤더가 없거나 형식이 올바르지 않음"));
                operation.getResponses().addApiResponse("403", errorResponse("관리자(admin)가 아님"));
            } else if (requiresMember(handlerMethod)) {
                operation.addSecurityItem(new SecurityRequirement().addList(ACCOUNT_HEADER_SCHEME));
                operation.getResponses().addApiResponse("401", errorResponse(
                        CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER + " 헤더가 없거나 형식이 올바르지 않음"));
            }
            return operation;
        };
    }

    private boolean requiresMember(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(CurrentMember.class));
    }

    private boolean requiresAdmin(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(CurrentAdmin.class));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(APPLICATION_JSON,
                        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF))));
    }
}
