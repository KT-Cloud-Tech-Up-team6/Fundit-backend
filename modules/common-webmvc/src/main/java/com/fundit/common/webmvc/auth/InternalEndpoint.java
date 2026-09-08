package com.fundit.common.webmvc.auth;

/**
 * 외부에 노출되면 안 되는 엔드포인트(HTTP 메서드 + 정확한 경로).
 * 게이트웨이 라우팅 제외와 짝을 이루는 두 번째 방어선이다 — 둘 중 하나만으로는
 * "게이트웨이를 우회한 직접 호출"이나 "게이트웨이를 통한 외부 노출" 중 한쪽이 뚫린다.
 */
public record InternalEndpoint(String method, String path) {
}
