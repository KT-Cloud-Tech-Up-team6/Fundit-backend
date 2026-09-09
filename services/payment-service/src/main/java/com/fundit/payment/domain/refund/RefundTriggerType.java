package com.fundit.payment.domain.refund;

import com.fundit.payment.application.event.PaymentEventPublisher.RefundReason;

/**
 * 환불 사유 유형. {@code SIMPLE_CHANGE_OF_MIND}/{@code GOAL_FAILED_AUTO}/{@code SHIPPING_DELAY}는
 * 판매자 검토 없이 즉시 처리되고(payment-service CLAUDE.md PAYMENT-008 구현 노트 — PaymentERD.md
 * 3장 코멘트는 SHIPPING_DELAY도 검토 단계를 거치는 것으로 서술하지만, PaymentApiSpec.md 2-4 및
 * CLAUDE.md가 "UNDER_REVIEW 단계 없음"으로 명시하고 있어 이 둘을 최신 기준으로 따른다),
 * {@code DEFECT}만 판매자 검토(UNDER_REVIEW→APPROVED/REJECTED) 단계를 거친다.
 */
public enum RefundTriggerType {
    SIMPLE_CHANGE_OF_MIND,
    GOAL_FAILED_AUTO,
    DEFECT,
    SHIPPING_DELAY,
    /** [신규, 정책 확인 필요] PAYMENT-017 전용 — 결제-재고만료 충돌 자동환불. */
    SYSTEM_RECONCILIATION;

    /**
     * order-service {@code PaymentEventListener.RefundReason}으로 매핑한다.
     *
     * <p>[정책 확인 필요] {@code SYSTEM_RECONCILIATION}은 order-service 쪽에 대응 enum이 없다
     * (order-service의 4종 고정 enum을 이 서비스가 임의로 늘릴 수 없음). 이 사유는 "결제는
     * 성공했지만 재고 만료로 시스템이 되돌리는" 케이스라 사용자 귀책이 아니며, order-service의
     * onRefundCompleted 처리상 {@code GOAL_FAILURE_AUTO_REFUND}만이 무조건 쿠폰을 복원하는
     * 시맨틱과 가장 가깝다고 판단해 임시로 매핑했다. order-service 담당자와 반드시 재확인할 것.
     */
    public RefundReason toOrderServiceReason() {
        return switch (this) {
            case GOAL_FAILED_AUTO, SYSTEM_RECONCILIATION -> RefundReason.GOAL_FAILURE_AUTO_REFUND;
            case SIMPLE_CHANGE_OF_MIND -> RefundReason.CANCELLED_BY_MEMBER;
            case DEFECT -> RefundReason.POST_SUCCESS_DEFECT;
            case SHIPPING_DELAY -> RefundReason.POST_SUCCESS_DELAY;
        };
    }
}
