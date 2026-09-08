package com.fundit.common.webmvc.auth;

import com.fundit.common.auth.AuthHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoginUserArgumentResolverUnitTest {

    private final LoginUserArgumentResolver resolver = new LoginUserArgumentResolver();

    @Test
    void LoginUser가_붙은_CurrentUser_파라미터만_지원한다() throws Exception {
        // given
        MethodParameter supported = methodParameter("annotated", 0);
        MethodParameter notAnnotated = methodParameter("notAnnotated", 0);
        MethodParameter wrongType = methodParameter("wrongType", 0);

        // when & then
        assertThat(resolver.supportsParameter(supported)).isTrue();
        assertThat(resolver.supportsParameter(notAnnotated)).isFalse();
        assertThat(resolver.supportsParameter(wrongType)).isFalse();
    }

    @Test
    void 게이트웨이가_주입한_헤더를_CurrentUser로_조립한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthHeaders.USER_ID, accountId.toString());
        request.addHeader(AuthHeaders.USER_ROLES, "MEMBER");

        // when
        CurrentUser user = (CurrentUser) resolver.resolveArgument(
                methodParameter("annotated", 0), null, new ServletWebRequest(request), null);

        // then
        assertThat(user.id()).isEqualTo(accountId);
        assertThat(user.roles()).containsExactly("MEMBER");
        assertThat(user.hasRole("MEMBER")).isTrue();
    }

    @Test
    void 권한이_콤마로_여러개_오면_모두_파싱한다() throws Exception {
        // given — 현재 auth-service는 단일 role만 발급하지만 헤더 포맷은 다중을 허용한다
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthHeaders.USER_ID, UUID.randomUUID().toString());
        request.addHeader(AuthHeaders.USER_ROLES, "MEMBER, ADMIN");

        // when
        CurrentUser user = (CurrentUser) resolver.resolveArgument(
                methodParameter("annotated", 0), null, new ServletWebRequest(request), null);

        // then
        assertThat(user.roles()).containsExactly("MEMBER", "ADMIN");
    }

    @Test
    void 권한_헤더가_없으면_빈_목록으로_조립한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthHeaders.USER_ID, UUID.randomUUID().toString());

        // when
        CurrentUser user = (CurrentUser) resolver.resolveArgument(
                methodParameter("annotated", 0), null, new ServletWebRequest(request), null);

        // then
        assertThat(user.roles()).isEmpty();
        assertThat(user.hasRole("MEMBER")).isFalse();
    }

    private MethodParameter methodParameter(String methodName, int index) throws NoSuchMethodException {
        return new MethodParameter(TargetController.class.getDeclaredMethod(methodName, parameterType(methodName)), index);
    }

    private Class<?> parameterType(String methodName) {
        return methodName.equals("wrongType") ? UUID.class : CurrentUser.class;
    }

    @SuppressWarnings("unused")
    static class TargetController {
        void annotated(@LoginUser CurrentUser user) {
        }

        void notAnnotated(CurrentUser user) {
        }

        void wrongType(@LoginUser UUID accountId) {
        }
    }
}
