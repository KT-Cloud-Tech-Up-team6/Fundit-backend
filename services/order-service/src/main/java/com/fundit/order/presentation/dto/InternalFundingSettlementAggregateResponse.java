package com.fundit.order.presentation.dto;

import com.fundit.order.application.funding.FundingInternalQueryService.SettlementAggregateSnapshot;

import java.util.List;

/**
 * 내부 전용 — payment-service가 정산(PAYMENT-009/012)에 쓰는 리워드·옵션별 판매 수량/금액과
 * 메이커 쿠폰 차감액 조회 응답. payment-service {@code HttpOrderSettlementAggregateClient}가
 * 이 필드 이름 그대로 역직렬화하므로 필드명을 바꾸면 그쪽 연동이 깨진다.
 */
public record InternalFundingSettlementAggregateResponse(List<LineItem> lineItems, long makerCouponDeductionAmount) {

    public static InternalFundingSettlementAggregateResponse from(SettlementAggregateSnapshot snapshot) {
        return new InternalFundingSettlementAggregateResponse(
                snapshot.lineItems().stream()
                        .map(li -> new LineItem(li.rewardId(), li.rewardName(), li.optionName(), li.quantity(), li.amount()))
                        .toList(),
                snapshot.makerCouponDeductionAmount());
    }

    public record LineItem(Long rewardId, String rewardName, String optionName, int quantity, long amount) {
    }
}
