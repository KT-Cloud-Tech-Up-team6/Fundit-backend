package com.fundit.member.infrastructure.security;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * POST /api/v1/members는 auth-service의 회원가입 내부 호출 전용이다(security.md 내부 전용
 * 엔드포인트 방어, CLAUDE.md 핵심 설계 결정). 게이트웨이 라우팅 제외와 별개로,
 * X-Internal-Api-Key 헤더가 설정값과 일치하지 않으면 여기서 차단한다.
 */
public class InternalApiKeyFilter extends HttpFilter {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final String expectedApiKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(String expectedApiKey, ObjectMapper objectMapper) {
        this.expectedApiKey = expectedApiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || !apiKey.equals(expectedApiKey)) {
            response.setStatus(CommonErrorCode.UNAUTHORIZED.getHttpStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(CommonErrorCode.UNAUTHORIZED));
            return;
        }
        chain.doFilter(request, response);
    }
}
