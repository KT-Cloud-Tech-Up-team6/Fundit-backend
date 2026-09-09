package com.fundit.payment.domain.payment;

public enum PaymentStatus {
    /** 결제 시도 생성됨(위젯 렌더링 전~인증 전). */
    PENDING,
    /** 토스 승인 API 성공. */
    COMPLETED,
    /** 승인 실패(재시도 가능, Funding.status는 order-service 쪽에서 PENDING 유지). */
    FAILED,
    /** 전액 취소 완료(payment_cancellations 합계 = amount). */
    CANCELLED
}
