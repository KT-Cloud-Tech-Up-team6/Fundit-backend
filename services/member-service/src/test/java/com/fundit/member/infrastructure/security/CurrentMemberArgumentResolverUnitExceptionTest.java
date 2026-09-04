package com.fundit.member.infrastructure.security;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentMemberArgumentResolverUnitExceptionTest {

    private final CurrentMemberArgumentResolver resolver = new CurrentMemberArgumentResolver();

    @Test
    void 헤더가_없으면_예외가_발생한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void 헤더값이_UUID_형식이_아니면_예외가_발생한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER, "not-a-uuid");

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void 헤더값이_공백뿐이면_예외가_발생한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER, "   ");

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }
}
