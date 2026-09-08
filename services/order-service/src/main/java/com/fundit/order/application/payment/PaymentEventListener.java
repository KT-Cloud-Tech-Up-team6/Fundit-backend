package com.fundit.order.application.payment;

/**
 * ORDER-015 — payment-service가 발행하는 결제완료/환불 이벤트를 구독해 이 서비스 소유
 * 테이블(coupon_issuances, 그리고 fundings.status)을 자체적으로 갱신한다(DB-per-service 원칙).
 *
 * payment-service가 아직 스캐폴딩되지 않아(루트 CLAUDE.md 착수 순서 참고) 실제 이벤트 페이로드가
 * 확정돼 있지 않다 — 이 인터페이스는 OrderDomainFunctionalSpec.md ORDER-015가 명시한 "fundingId,
 * couponIssuanceId, 환불유형, 전액환불여부"를 그대로 옮긴 것이다[가정]. payment-service 쪽 이벤트
 * 스키마가 확정되면 필드명을 맞추고, 브로커가 정해지면 이 포트를 호출하는 리스너 어댑터를
 * infrastructure/event에 추가하면 된다(ORDER-016과 동일한 "골격 우선" 접근).
 */
public interface PaymentEventListener {

    /**
     * 결제 성공 — 이 이벤트를 트리거로 (1) 쿠폰을 사용완료 처리하고 (2) PENDING 상태의 Funding을
     * 결제 완료 상태로 전환한다. (2)는 ORDER-0XX로 번호가 매겨져 있지 않지만, 그렇지 않으면
     * 결제에 성공한 주문이 영영 PENDING에 머무르게 되어 필요한 처리다[가정].
     */
    void onPaymentCompleted(PaymentCompletedEvent event);

    /** 환불 완료 — 환불 유형에 따라 쿠폰 복원 여부와 Funding 상태를 함께 반영한다. */
    void onRefundCompleted(RefundCompletedEvent event);

    record PaymentCompletedEvent(Long fundingId, Long couponIssuanceId) {
    }

    /**
     * GOAL_FAILURE_AUTO_REFUND: 목표 미달 자동환불(쿠폰 복원) / CANCELLED_BY_MEMBER: 마감 전
     * 단순변심 취소(쿠폰 미복원 — ORDER-014 API 흐름에서 이미 별도 처리되므로 여기서는 무시) /
     * POST_SUCCESS_DEFECT, POST_SUCCESS_DELAY: 성립 후 하자·지연 환불(전액환불 건만 복원).
     */
    enum RefundReason {
        GOAL_FAILURE_AUTO_REFUND, CANCELLED_BY_MEMBER, POST_SUCCESS_DEFECT, POST_SUCCESS_DELAY
    }

    record RefundCompletedEvent(Long fundingId, Long couponIssuanceId, RefundReason refundReason, boolean fullRefund) {
    }
}
