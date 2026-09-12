package com.fundit.fulfillment.domain;

import com.fundit.common.error.ErrorCode;
import lombok.Getter;

/**
 * fulfillment-service 전용 에러 코드(서비스당 flat enum 1개 — error-handling.md 컨벤션).
 * CommonErrorCode에 이미 있는 INVALID_INPUT/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/CONFLICT/
 * BUSINESS_RULE_VIOLATION/DEPENDENCY_FAILURE는 재정의하지 않는다
 * (FulfillmentApiSpec.md "에러 코드 매핑" 표 기준).
 */
@Getter
public enum FulfillmentErrorCode implements ErrorCode {

    ALREADY_SHIPPED(409, "이미 발송 처리된 건입니다."),
    NOT_YET_DELIVERED(422, "아직 배송완료 전입니다."),
    INVALID_STAGE_TRANSITION(422, "이미 지난 단계입니다.");

    private final int httpStatus;
    private final String message;

    FulfillmentErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
