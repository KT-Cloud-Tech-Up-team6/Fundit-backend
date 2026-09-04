package com.fundit.member.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentMemberArgumentResolverUnitTest {

    private final CurrentMemberArgumentResolver resolver = new CurrentMemberArgumentResolver();

    @Test
    void X_Account_Id_헤더가_있으면_UUID로_변환한다() {
        // given
        UUID accountId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER, accountId.toString());

        // when
        Object result = resolver.resolveArgument(null, null, new ServletWebRequest(request), null);

        // then
        assertThat(result).isEqualTo(accountId);
    }

    @Test
    void CurrentMember_애노테이션과_UUID_타입이면_지원한다() throws NoSuchMethodException {
        // given
        MethodParameter parameter = new MethodParameter(
                Target.class.getDeclaredMethod("withAnnotation", UUID.class), 0);

        // when & then
        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    void 애노테이션이_없으면_지원하지_않는다() throws NoSuchMethodException {
        // given
        MethodParameter parameter = new MethodParameter(
                Target.class.getDeclaredMethod("withoutAnnotation", UUID.class), 0);

        // when & then
        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    void 애노테이션은_있지만_UUID_타입이_아니면_지원하지_않는다() throws NoSuchMethodException {
        // given
        MethodParameter parameter = new MethodParameter(
                Target.class.getDeclaredMethod("withAnnotationWrongType", String.class), 0);

        // when & then
        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @SuppressWarnings("unused")
    private static class Target {
        void withAnnotation(@CurrentMember UUID id) {
        }

        void withoutAnnotation(UUID id) {
        }

        void withAnnotationWrongType(@CurrentMember String id) {
        }
    }
}
