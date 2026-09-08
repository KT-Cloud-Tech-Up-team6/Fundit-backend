package com.fundit.order.domain;

import com.fundit.common.error.ErrorCode;
import lombok.Getter;

/**
 * order-service 전용 에러 코드(서비스당 flat enum 1개 — error-handling.md 컨벤션).
 * CommonErrorCode에 이미 있는 INVALID_INPUT/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/CONFLICT/
 * RESOURCE_EXPIRED/BUSINESS_RULE_VIOLATION/DEPENDENCY_FAILURE는 재정의하지 않는다
 * (OrderDomainApiSpec.md "에러 코드 매핑" 표 기준).
 */
@Getter
public enum OrderErrorCode implements ErrorCode {

    INSUFFICIENT_STOCK(409, "재고가 부족합니다."),
    ORDER_NOT_CANCELLABLE(422, "펀딩이 종료되어 취소할 수 없습니다."),
    COUPON_EXHAUSTED(409, "쿠폰이 모두 소진되었습니다."),
    COUPON_NOT_APPLICABLE(422, "적용할 수 없는 쿠폰입니다."),
    COUPON_BUDGET_EXCEEDED(422, "쿠폰 발급 예산 한도를 초과했습니다.");

    private final int httpStatus;
    private final String message;

    OrderErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
