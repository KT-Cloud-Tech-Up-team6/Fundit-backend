package com.fundit.order.domain.funding;

/**
 * 펀딩 내역 화면의 진행 단계 배지(FE 요청). 주문 상태({@link FundingStatus})만으로는 만들 수 없다 —
 * 성립(GOAL_ACHIEVED) 하나가 화면에서는 "펀딩 성공 / 발송 지연 / 배송 중 / 배송 완료"로 갈리고,
 * 그 구분은 fulfillment-service가 가진 배송 상태에서 온다.
 *
 * <p>주문 상태를 대체하지 않는다 — 응답에는 {@code status}(FundingStatus)와 함께 내려간다.
 */
public enum FundingProgressStage {
    /** 모금 중. 미결제(PENDING)도 화면에서는 같은 배지다 — 결제 대기는 별도 배지가 아니라 잔여 시간으로 보여준다. */
    FUNDING_IN_PROGRESS,
    /** 성립 후 제작·발송 준비 중(발송 예정일 이내). */
    FUNDING_SUCCEEDED,
    /** 성립 후 미발송인데 발송 예정일이 지났다 — 이 상태에서만 참여 취소(발송지연 환불)가 가능하다. */
    SHIPPING_DELAYED,
    SHIPPING,
    DELIVERED,
    GOAL_FAILED,
    CANCELLED,
    PAYMENT_EXPIRED,
    /** 성립 후 환불·반품 완료. */
    REFUNDED
}
