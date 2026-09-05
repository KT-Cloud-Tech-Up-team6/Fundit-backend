package com.fundit.project.infrastructure.security;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * PROJECT-030(심사 승인/반려)처럼 관리자 전용인 엔드포인트에서 쓴다.
 * CurrentMemberArgumentResolver와 마찬가지로 게이트웨이가 없는 현재는 X-Account-Id/X-Account-Role
 * 헤더를 그대로 신뢰하는 임시 조치다 — auth-service의 role 값(member/admin, 소문자) 기준으로
 * role이 admin이 아니면 403(FORBIDDEN)으로 막는다. 헤더 자체가 없으면 401(UNAUTHORIZED).
 */
@Component
public class CurrentAdminArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String ACCOUNT_ROLE_HEADER = "X-Account-Role";
    private static final String ADMIN_ROLE = "admin";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentAdmin.class)
                && parameter.getParameterType().equals(UUID.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        UUID accountId = CurrentMemberArgumentResolver.resolveAccountId(webRequest);

        String role = webRequest.getHeader(ACCOUNT_ROLE_HEADER);
        if (role == null || !ADMIN_ROLE.equalsIgnoreCase(role)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return accountId;
    }
}
