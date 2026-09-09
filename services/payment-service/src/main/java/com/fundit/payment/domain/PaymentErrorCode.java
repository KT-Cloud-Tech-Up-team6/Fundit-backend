package com.fundit.payment.domain;

import com.fundit.common.error.ErrorCode;
import lombok.Getter;

/**
 * payment-service 전용 에러 코드(서비스당 flat enum 1개 — error-handling.md 컨벤션).
 * CommonErrorCode에 이미 있는 INVALID_INPUT/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/CONFLICT/
 * RESOURCE_EXPIRED/BUSINESS_RULE_VIOLATION/DEPENDENCY_FAILURE는 재정의하지 않는다
 * (payment-service CLAUDE.md "에러 코드" 표 기준 — PaymentApiSpec.md 초안보다 이 목록이 최신).
 */
@Getter
public enum PaymentErrorCode implements ErrorCode {

    FUNDING_NOT_PENDING(409, "결제 시도 대상 주문이 결제 가능한 상태가 아닙니다."),
    PAYMENT_NOT_PENDING(409, "승인 대상 결제가 대기 상태가 아닙니다."),
    PAYMENT_AMOUNT_MISMATCH(422, "승인 요청 금액이 결제 시도 시점 금액과 일치하지 않습니다."),
    PAYMENT_EXPIRED(410, "결제 인증 유효 시간이 초과되었습니다."),
    PG_CONFIRM_FAILED(422, "결제 승인에 실패했습니다."),
    PG_CANCEL_FAILED(422, "결제 취소에 실패했습니다."),
    WEBHOOK_SIGNATURE_INVALID(401, "웹훅 서명 검증에 실패했습니다."),
    EVIDENCE_REQUIRED(400, "증빙 자료가 필요합니다."),
    REASON_REQUIRED(400, "반려 사유가 필요합니다."),
    ALREADY_SHIPPED(409, "이미 발송이 시작되어 취소할 수 없습니다."),
    DISPUTE_PERIOD_EXPIRED(409, "정산 이의신청 가능 기간이 지났습니다.");

    private final int httpStatus;
    private final String message;

    PaymentErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
