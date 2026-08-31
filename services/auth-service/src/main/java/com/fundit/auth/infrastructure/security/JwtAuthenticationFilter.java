package com.fundit.auth.infrastructure.security;

import com.fundit.common.error.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer 파싱 → 유효하면 SecurityContext에 인증정보 세팅.
 * 토큰이 없거나 무효/만료여도 여기서 막지 않는다(공개 엔드포인트가 대부분이라
 * 그냥 익명으로 통과시키고, 인증이 필요한 엔드포인트는 SecurityConfig의
 * authenticated() 규칙 + AuthenticationEntryPoint가 401을 응답한다).
 * 이때 실패 사유(만료/서명무효)를 AuthenticationEntryPoint가 구분해서 응답할 수 있도록
 * request attribute에 실어둔다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "jwtAuthError";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                var claims = jwtTokenProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()));
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()));
                var authentication = new UsernamePasswordAuthenticationToken(claims.accountId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException e) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e);
                SecurityContextHolder.clearContext();
            } catch (RuntimeException e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
