package com.fundit.project.infrastructure.openapi;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.webmvc.auth.LoginUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;

/**
 * 로컬에서 게이트웨이 없이 project-service 자체 Swagger UI로 API를 하나씩 테스트할 수 있게,
 * 게이트웨이가 실제로 주입하는 헤더(X-User-Id/X-User-Roles/X-Internal-Api-Key)를 Swagger
 * Authorize 입력란으로 노출한다.
 *
 * <p>CommonOpenApiConfig(modules:common-webmvc)가 문서화하는 {@code bearerAuth}(JWT)는
 * 게이트웨이가 검증해서 위 헤더로 바꿔주는 값이라, 게이트웨이 없이 이 서비스에 직접 호출할 때는
 * 넣어도 의미가 없다 — 로컬 테스트 전용 보정이라 project-service에만 둔다.
 */
@Configuration
public class GatewayHeaderOpenApiCustomizer {

    private static final String USER_ID_SCHEME = "gatewayUserIdHeader";
    private static final String USER_ROLES_SCHEME = "gatewayUserRolesHeader";
    private static final String INTERNAL_KEY_SCHEME = "gatewayInternalApiKeyHeader";

    @Bean
    public GlobalOpenApiCustomizer gatewayHeaderSchemeCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents() != null ? openApi.getComponents() : new Components();
            components.addSecuritySchemes(USER_ID_SCHEME, headerScheme(AuthHeaders.USER_ID,
                    "로컬 테스트용 — 실제로는 게이트웨이가 JWT의 sub 클레임에서 꺼내 주입하는 계정 ID(UUID)."));
            components.addSecuritySchemes(USER_ROLES_SCHEME, headerScheme(AuthHeaders.USER_ROLES,
                    "로컬 테스트용 — 관리자 전용 API(PROJECT-030)는 ADMIN을 넣어야 한다."));
            components.addSecuritySchemes(INTERNAL_KEY_SCHEME, headerScheme(AuthHeaders.INTERNAL_API_KEY,
                    "로컬 테스트용 — application-local.yml의 internal-api.key와 동일한 값이어야 InternalGatewaySecretFilter를 통과한다."));
            openApi.setComponents(components);
        };
    }

    @Bean
    public GlobalOperationCustomizer gatewayHeaderRequirementCustomizer() {
        return (operation, handlerMethod) -> {
            if (requiresLogin(handlerMethod)) {
                operation.addSecurityItem(new SecurityRequirement()
                        .addList(USER_ID_SCHEME).addList(USER_ROLES_SCHEME).addList(INTERNAL_KEY_SCHEME));
            }
            return operation;
        };
    }

    private boolean requiresLogin(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(LoginUser.class));
    }

    private SecurityScheme headerScheme(String headerName, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName)
                .description(description);
    }
}
