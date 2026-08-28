package com.fundit.common.error;

/**
 * 에러 응답 바디. API 버저닝·표준 에러 형식 정의서 §2의 포맷을 그대로 따른다.
 * {"code": "PAID_ALREADY", "message": "결제되었습니다.", "detail": null}
 *
 * detail은 타입을 고정하지 않았다 — 검증 오류처럼 필드별 상세가 필요한 경우도 있고,
 * 단순 비즈니스 예외처럼 null인 경우도 있어서 유연하게 Object로 뒀다.
 */
public record ErrorResponse(String code, String message, Object detail) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, Object detail) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), detail);
    }
}