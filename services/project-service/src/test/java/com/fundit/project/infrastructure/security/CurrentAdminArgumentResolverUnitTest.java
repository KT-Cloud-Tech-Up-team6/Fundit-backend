package com.fundit.project.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentAdminArgumentResolverUnitTest {

    private final CurrentAdminArgumentResolver resolver = new CurrentAdminArgumentResolver();

    @Test
    void role이_admin이면_accountId를_반환한다() {
        // given
        UUID accountId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER, accountId.toString());
        request.addHeader(CurrentAdminArgumentResolver.ACCOUNT_ROLE_HEADER, "admin");

        // when
        Object result = resolver.resolveArgument(null, null, new ServletWebRequest(request), null);

        // then
        assertThat(result).isEqualTo(accountId);
    }

    @Test
    void role값_대소문자와_무관하게_admin이면_통과한다() {
        // given
        UUID accountId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER, accountId.toString());
        request.addHeader(CurrentAdminArgumentResolver.ACCOUNT_ROLE_HEADER, "ADMIN");

        // when
        Object result = resolver.resolveArgument(null, null, new ServletWebRequest(request), null);

        // then
        assertThat(result).isEqualTo(accountId);
    }

    @Test
    void CurrentAdmin_애노테이션과_UUID_타입이면_지원한다() throws NoSuchMethodException {
        // given
        MethodParameter parameter = new MethodParameter(
                Target.class.getDeclaredMethod("withAnnotation", UUID.class), 0);

        // when & then
        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @SuppressWarnings("unused")
    private static class Target {
        void withAnnotation(@CurrentAdmin UUID id) {
        }
    }
}
