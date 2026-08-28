package com.fundit.common.error;

import lombok.Getter;

/**
 * 여러 서비스에서 공통으로 사용하는 에러 코드.
 * 규칙: 이 목록에 있는 코드는 새 도메인 코드로 재정의하지 않고 그대로 사용한다.
 * (출처: API 버저닝·표준 에러 형식 정의서 §4)
 *
 * message는 정의서의 "설명" 컬럼을 그대로 사용했습니다.
 * 사용자에게 노출하기엔 다소 딱딱한 문구도 있어서(예: INVALID_INPUT),
 * 필요하면 더 친절한 문구로 바꾸셔도 됩니다.
 */
@Getter
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(400, "입력값 유효성 오류 (Body 누락·JSON 형식 오류 포함)"),
    UNAUTHORIZED(401, "인증 필요"),
    TOKEN_EXPIRED(401, "Access Token 만료"),
    TOKEN_INVALID(401, "Access Token 유효성 검증 실패"),
    FORBIDDEN(403, "접근 권한 없음"),
    NOT_FOUND(404, "리소스 없음"),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 HTTP 메소드"),
    CONFLICT(409, "리소스 충돌 (중복)"),
    RESOURCE_EXPIRED(410, "리소스 만료 (예: 주문 만료)"),
    BUSINESS_RULE_VIOLATION(422, "비즈니스 규칙 위반"),
    RESOURCE_LOCKED(423, "리소스가 잠겨있음"),
    TOO_MANY_REQUESTS(429, "요청 한도 초과"),
    INTERNAL_ERROR(500, "서버 내부 오류"),
    SERVICE_UNAVAILABLE(503, "서비스 일시적 사용 불가"),
    DEPENDENCY_FAILURE(503, "외부 서비스(Auth, Redis 등) 호출 실패");

    private final int httpStatus;
    private final String message;

    CommonErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}