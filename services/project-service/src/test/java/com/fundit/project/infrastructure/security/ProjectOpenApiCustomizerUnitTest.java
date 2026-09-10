package com.fundit.project.infrastructure.security;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링 컨텍스트를 띄우지 않는다 — CI에 DB가 없고 이 레포에서 이미 깨진 지점이다(AuthOpenApiCustomizerUnitTest와 동일 이유).
 * 커스터마이저에 직접 OpenAPI/Operation 객체를 넣고 결과만 본다.
 */
class ProjectOpenApiCustomizerUnitTest {

    private final ProjectOpenApiCustomizer customizer = new ProjectOpenApiCustomizer();

    @Test
    void accountIdHeader와_accountRoleHeader_보안스킴이_모두_컴포넌트에_등록된다() {
        // given — 스킴이 없으면 @CurrentMember/@CurrentAdmin에 붙이는 SecurityRequirement가 참조할 대상이 없다
        OpenAPI openApi = new OpenAPI();

        // when
        customizer.accountHeaderSchemeCustomizer().customise(openApi);

        // then
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKeys("accountIdHeader", "accountRoleHeader");
        assertThat(openApi.getComponents().getSecuritySchemes().get("accountIdHeader").getName())
                .isEqualTo(CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER);
        assertThat(openApi.getComponents().getSecuritySchemes().get("accountRoleHeader").getName())
                .isEqualTo(CurrentAdminArgumentResolver.ACCOUNT_ROLE_HEADER);
    }

    @Test
    void CurrentMember를_받는_오퍼레이션에만_인증_요구와_401이_붙는다() throws Exception {
        // given — 공통 설정(CommonOpenApiConfig)은 @LoginUser만 보므로 이 서비스에선 아무 오퍼레이션도 잡히지 않는다
        Operation secured = new Operation().responses(new ApiResponses());
        Operation open = new Operation().responses(new ApiResponses());

        // when
        customizer.accountHeaderRequirementCustomizer().customize(secured, handlerMethod("memberOnly"));
        customizer.accountHeaderRequirementCustomizer().customize(open, handlerMethod("public_"));

        // then
        assertThat(secured.getSecurity()).isNotEmpty();
        assertThat(secured.getResponses().get("401")).isNotNull();
        assertThat(secured.getResponses().get("403")).isNull();
        assertThat(open.getSecurity()).isNull();
        assertThat(open.getResponses().get("401")).isNull();
    }

    @Test
    void CurrentAdmin을_받는_오퍼레이션에는_401과_403이_모두_붙는다() throws Exception {
        // given — 관리자 전용은 헤더 자체가 없을 때(401)와 admin이 아닐 때(403)를 둘 다 문서화해야 한다
        Operation adminOnly = new Operation().responses(new ApiResponses());

        // when
        customizer.accountHeaderRequirementCustomizer().customize(adminOnly, handlerMethod("adminOnly"));

        // then
        assertThat(adminOnly.getSecurity()).isNotEmpty();
        assertThat(adminOnly.getResponses().get("401")).isNotNull();
        assertThat(adminOnly.getResponses().get("403")).isNotNull();
    }

    @Test
    void CurrentAdmin을_받는_오퍼레이션은_두_헤더_스킴을_함께_요구한다() throws Exception {
        // given — X-Account-Role은 설명 문구가 아니라 스펙 자체에 "같이 필요하다"고 기계적으로 드러나야 한다
        Operation adminOnly = new Operation().responses(new ApiResponses());
        Operation memberOnly = new Operation().responses(new ApiResponses());

        // when
        customizer.accountHeaderRequirementCustomizer().customize(adminOnly, handlerMethod("adminOnly"));
        customizer.accountHeaderRequirementCustomizer().customize(memberOnly, handlerMethod("memberOnly"));

        // then — 같은 SecurityRequirement 안에 두 스킴이 함께 있어야 "AND"(둘 다 필요)로 해석된다
        assertThat(adminOnly.getSecurity()).hasSize(1);
        assertThat(adminOnly.getSecurity().get(0)).containsOnlyKeys("accountIdHeader", "accountRoleHeader");
        assertThat(memberOnly.getSecurity().get(0)).containsOnlyKeys("accountIdHeader");
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new TargetController(), TargetController.class.getMethod(methodName, UUID.class));
    }

    @SuppressWarnings("unused")
    static class TargetController {
        public void memberOnly(@CurrentMember UUID memberId) {
        }

        public void adminOnly(@CurrentAdmin UUID adminId) {
        }

        public void public_(UUID id) {
        }
    }
}
