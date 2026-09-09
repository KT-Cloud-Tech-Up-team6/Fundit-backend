package com.fundit.payment.application.settlement;

import java.util.List;

/**
 * order-service의 정산 집계 데이터(리워드/옵션별 판매 수량·금액, 메이커 쿠폰 차감액) 조회
 * 아웃바운드 포트(PAYMENT-009/012). payment-service CLAUDE.md가 "OrderFundingClient와 동일하게
 * 스텁 처리"하라고 명시한 대상 — order-service에 아직 노출 API가 없다.
 */
public interface OrderSettlementAggregateClient {

    List<LineItemAggregate> fetchLineItems(Long fundingId);

    /** PAYMENT-012 — 메이커 발급 쿠폰만 정산에서 차감한다(플랫폼 발급 쿠폰은 차감하지 않음). */
    long fetchMakerCouponDeductionAmount(Long fundingId);

    record LineItemAggregate(Long rewardId, String rewardName, String optionName, int quantity, long amount) {
    }
}
