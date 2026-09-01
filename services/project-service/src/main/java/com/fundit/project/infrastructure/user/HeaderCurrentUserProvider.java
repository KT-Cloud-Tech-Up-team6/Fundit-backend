package com.fundit.project.infrastructure.user;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * 인증 방식이 확정되기 전까지 쓰는 임시 어댑터. 게이트웨이가 JWT를 검증하고 신원을 헤더로
 * 넘겨주는 형태를 가정한다.
 * <p>
 * 각 서비스가 JWT를 직접 검증하는 쪽으로 결정되면 이 클래스를 JWT 파싱 어댑터로 교체한다 —
 * 포트(CurrentUserProvider)를 쓰는 application 계층은 건드릴 필요가 없다.
 * 지금 상태로는 헤더를 위조하면 아무나 다른 사용자로 행세할 수 있으므로,
 * 게이트웨이가 외부 요청의 이 헤더를 반드시 제거하도록 설정되기 전에는 외부에 노출하면 안 된다.
 */
@Component
public class HeaderCurrentUserProvider implements CurrentUserProvider {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    public Optional<CurrentUser> find() {
        return currentRequest()
                .map(request -> request.getHeader(USER_ID_HEADER))
                .filter(value -> !value.isBlank())
                .flatMap(this::parseUuid)
                .map(id -> new CurrentUser(id, resolveRole()));
    }

    @Override
    public CurrentUser require() {
        return find().orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }

    private Role resolveRole() {
        String raw = currentRequest().map(request -> request.getHeader(USER_ROLE_HEADER)).orElse(null);
        if (raw == null || raw.isBlank()) {
            return Role.MEMBER;
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Role.MEMBER;
        }
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    private Optional<HttpServletRequest> currentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest);
    }
}
