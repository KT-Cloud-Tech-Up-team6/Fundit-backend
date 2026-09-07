package com.fundit.project.infrastructure.security;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentAdminArgumentResolverUnitExceptionTest {

    private final CurrentAdminArgumentResolver resolver = new CurrentAdminArgumentResolver();

    @Test
    void 계정헤더가_없으면_401_예외가_발생한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void role_헤더가_없으면_403_예외가_발생한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER, UUID.randomUUID().toString());

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void role이_admin이_아니면_403_예외가_발생한다() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER, UUID.randomUUID().toString());
        request.addHeader(CurrentAdminArgumentResolver.ACCOUNT_ROLE_HEADER, "member");

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }
}
