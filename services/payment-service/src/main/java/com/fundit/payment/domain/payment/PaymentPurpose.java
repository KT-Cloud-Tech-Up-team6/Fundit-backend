package com.fundit.payment.domain.payment;

/**
 * 결제의 용도. 같은 펀딩에 리워드 결제(PAYMENT-001/002)와 교환 배송비 결제가 함께 존재할 수
 * 있어 둘을 구분한다 — 환불·정산·이벤트 발행은 {@link #REWARD}만 대상이다(교환비는 취소 대상도,
 * order-service에 알릴 결제도 아니다).
 */
public enum PaymentPurpose {
    REWARD,
    /** 구매자 귀책 교환의 교환 배송비(환불 정책 V.1.0) — 판매자 승인 후 구매자가 별도 결제한다. */
    EXCHANGE_FEE
}
