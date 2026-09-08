package com.fundit.common.auth;

/**
 * 게이트웨이 ↔ 다운스트림 서비스 간 헤더 계약.
 *
 * <p>게이트웨이(platform:gateway-service, WebFlux)와 각 서비스(modules:common-webmvc, Servlet)는
 * 스택이 달라 코드를 공유할 수 없지만, 이 헤더 이름만큼은 반드시 일치해야 한다. 양쪽이 각자
 * 문자열 리터럴로 들고 있으면 한쪽만 바뀌어도 조용히 인증이 깨진다 — Phase 1에서
 * {@code X-Internal-Api-Key}를 받는 쪽만 만들고 보내는 쪽이 누락돼 회원가입이 100% 실패했던
 * 전례가 정확히 이 형태였다.
 *
 * <p>modules:common에 두는 근거는 루트 {@code CLAUDE.md}의 "서비스 경계를 넘는 계약 클래스만 둔다"
 * 항목이다 — ① 프레임워크 타입 무의존 ② 로직 없음 ③ 다른 스택의 모듈이 같은 값을 공유해야 함,
 * 세 조건을 모두 만족한다. 한 서비스만 쓰는 상수라면 그 서비스에 둘 것.
 */
public final class AuthHeaders {

    /** 게이트웨이가 JWT의 {@code sub} 클레임에서 꺼내 주입하는 계정 ID(UUID 문자열). */
    public static final String USER_ID = "X-User-Id";

    /** 게이트웨이가 JWT의 {@code role} 클레임에서 꺼내 주입하는 권한 목록(콤마 구분). */
    public static final String USER_ROLES = "X-User-Roles";

    /**
     * 요청이 신뢰 경로(게이트웨이 또는 다른 내부 서비스)를 통해 들어왔음을 증명하는 공유 시크릿.
     * 이게 없으면 서비스 포트로 직접 {@link #USER_ID}를 위조해 보내는 걸 막을 수 없다.
     */
    public static final String INTERNAL_API_KEY = "X-Internal-Api-Key";

    private AuthHeaders() {
    }
}
