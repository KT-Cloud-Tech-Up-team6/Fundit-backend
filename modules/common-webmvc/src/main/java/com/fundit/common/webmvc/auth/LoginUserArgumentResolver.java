package com.fundit.common.webmvc.auth;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 게이트웨이가 주입한 {@link AuthHeaders#USER_ID}/{@link AuthHeaders#USER_ROLES} 헤더를
 * {@link CurrentUser}로 조립한다.
 *
 * <p>이 헤더를 신뢰해도 되는 근거는 {@link InternalGatewaySecretFilter}가 앞단에서
 * 내부 키를 검증했다는 것뿐이다 — 이 리졸버 단독으로는 위조를 구분할 수 없으므로
 * 그 필터를 빼고 이것만 등록하면 안 된다.
 */
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String userId = webRequest.getHeader(AuthHeaders.USER_ID);
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        try {
            return new CurrentUser(UUID.fromString(userId), parseRoles(webRequest.getHeader(AuthHeaders.USER_ROLES)));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    private List<String> parseRoles(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();
    }
}
