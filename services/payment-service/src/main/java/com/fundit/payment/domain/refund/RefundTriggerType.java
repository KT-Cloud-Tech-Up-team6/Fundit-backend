package com.fundit.payment.domain.refund;

import com.fundit.payment.application.event.PaymentEventPublisher.RefundReason;

import java.util.EnumSet;
import java.util.Set;

/**
 * 환불 사유 유형. {@code GOAL_FAILED_AUTO}/{@code SHIPPING_DELAY}/{@code SIMPLE_CHANGE_OF_MIND}는
 * 판매자 검토 없이 즉시 처리되고(payment-service CLAUDE.md PAYMENT-008 구현 노트 — PaymentERD.md
 * 3장 코멘트는 SHIPPING_DELAY도 검토 단계를 거치는 것으로 서술하지만, PaymentApiSpec.md 2-4 및
 * CLAUDE.md가 "UNDER_REVIEW 단계 없음"으로 명시하고 있어 이 둘을 최신 기준으로 따른다),
 * {@code DEFECT}/{@code RETURN_CHANGE_OF_MIND}는 판매자 검토 단계를 거친다.
 */
public enum RefundTriggerType {
    /**
     * 모금 중 참여 취소(order-service ORDER-014 → {@code FundingCancelledByMember} 이벤트) 전용.
     * 성립 이후 단순변심 취소는 환불 정책 V.1.0에서 불가이고, 발송 후 단순변심은
     * {@link #RETURN_CHANGE_OF_MIND}로 접수한다.
     */
    SIMPLE_CHANGE_OF_MIND,
    GOAL_FAILED_AUTO,
    DEFECT,
    SHIPPING_DELAY,
    /** [신규, 정책 확인 필요] PAYMENT-017 전용 — 결제-재고만료 충돌 자동환불. */
    SYSTEM_RECONCILIATION,
    /**
     * [신규, MVP 범위 제한] 교환 신청. 환불(결제취소)이 아니라 재발송이 필요한 별개 흐름이라
     * {@link #toOrderServiceReason()}으로 완결되지 않는다 — 신청·목록조회까지만 지원하고,
     * 승인/완료(재발송 연동, fulfillment-service 협의 필요)는 별도 설계가 끝나기 전까지 없다.
     */
    EXCHANGE,
    /**
     * 발송 후(수령 후) 구매자 귀책 반품 — 단순변심·옵션 선택 오류(환불 정책 V.1.0). 판매자가
     * 회수를 확인하고 승인하면 반품 배송비({@link ReturnPolicy#RETURN_SHIPPING_FEE})를 뺀
     * 금액만 부분취소된다.
     */
    RETURN_CHANGE_OF_MIND;

    /** 판매자 검토(승인/반려) 대상 — 신청만 접수해두고 결정 API에서 취소를 실행한다. */
    private static final Set<RefundTriggerType> SELLER_DECISION_TYPES = EnumSet.of(DEFECT, RETURN_CHANGE_OF_MIND);

    /** 발송 후(배송 완료 후) 신청 유형 — 수령 후 7일 기한과 중복 신청 검사를 공유한다. */
    private static final Set<RefundTriggerType> POST_SHIPMENT_TYPES = EnumSet.of(DEFECT, EXCHANGE,
            RETURN_CHANGE_OF_MIND);

    public boolean isSellerDecisionTarget() {
        return SELLER_DECISION_TYPES.contains(this);
    }

    public boolean isPostShipmentRequest() {
        return POST_SHIPMENT_TYPES.contains(this);
    }

    public static Set<RefundTriggerType> postShipmentTypes() {
        return POST_SHIPMENT_TYPES;
    }

    /**
     * order-service {@code PaymentEventListener.RefundReason}으로 매핑한다.
     *
     * <p>[정책 확인 필요] {@code SYSTEM_RECONCILIATION}은 order-service 쪽에 대응 enum이 없다
     * (order-service의 고정 enum을 이 서비스가 임의로 늘릴 수 없음). 이 사유는 "결제는
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
            case RETURN_CHANGE_OF_MIND -> RefundReason.POST_SUCCESS_RETURN;
            case EXCHANGE -> throw new UnsupportedOperationException(
                    "EXCHANGE는 결제취소를 수반하지 않아 RefundExecutionService로 완료 처리하지 않는다.");
        };
    }
}
