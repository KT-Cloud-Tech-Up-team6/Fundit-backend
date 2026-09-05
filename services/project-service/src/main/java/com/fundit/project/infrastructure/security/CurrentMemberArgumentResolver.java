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
 * ponytail: 게이트웨이가 아직 없어 서명 검증 없이 X-Account-Id 헤더를 그대로 신뢰한다
 * (member-service CurrentMemberArgumentResolver와 동일한 임시 조치, 내부망 전제).
 * 게이트웨이가 JWT를 파싱해 이 헤더를 주입하게 되면 이 리졸버는 그대로 재사용 가능 —
 * 게이트웨이 구현 시 "헤더가 신뢰 가능한 경로로만 들어오는지" 반드시 재검증할 것.
 */
@Component
public class CurrentMemberArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String ACCOUNT_ID_HEADER = "X-Account-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMember.class)
                && parameter.getParameterType().equals(UUID.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return resolveAccountId(webRequest);
    }

    static UUID resolveAccountId(NativeWebRequest webRequest) {
        String header = webRequest.getHeader(ACCOUNT_ID_HEADER);
        if (header == null || header.isBlank()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
