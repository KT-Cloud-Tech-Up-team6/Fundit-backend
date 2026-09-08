package com.fundit.common.webmvc.auth;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginUserArgumentResolverUnitExceptionTest {

    private final LoginUserArgumentResolver resolver = new LoginUserArgumentResolver();

    @Test
    void 사용자_헤더가_없으면_UNAUTHORIZED_예외가_발생한다() throws Exception {
        // given — 게이트웨이가 토큰을 못 받았거나 검증에 실패해 헤더를 안 붙인 경우
        var request = new ServletWebRequest(new MockHttpServletRequest());

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(annotatedParameter(), null, request, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void 사용자_헤더가_비어있으면_UNAUTHORIZED_예외가_발생한다() throws Exception {
        // given
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(AuthHeaders.USER_ID, "   ");

        // when & then
        assertThatThrownBy(() ->
                resolver.resolveArgument(annotatedParameter(), null, new ServletWebRequest(servletRequest), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void 사용자_헤더가_UUID_형식이_아니면_UNAUTHORIZED_예외가_발생한다() throws Exception {
        // given
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(AuthHeaders.USER_ID, "not-a-uuid");

        // when & then
        assertThatThrownBy(() ->
                resolver.resolveArgument(annotatedParameter(), null, new ServletWebRequest(servletRequest), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    private MethodParameter annotatedParameter() throws NoSuchMethodException {
        return new MethodParameter(
                LoginUserArgumentResolverUnitTest.TargetController.class
                        .getDeclaredMethod("annotated", CurrentUser.class), 0);
    }
}
