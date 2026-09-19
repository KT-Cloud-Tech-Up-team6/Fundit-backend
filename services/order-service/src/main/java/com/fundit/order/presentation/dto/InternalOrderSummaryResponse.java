package com.fundit.order.presentation.dto;

import com.fundit.order.application.funding.FundingInternalQueryService.OrderSummarySnapshot;

import java.util.List;
import java.util.UUID;

/** 내부 전용 — payment-service 환불 목록(V04)이 배치로 호출하는 주문 요약 조회 응답. */
public record InternalOrderSummaryResponse(UUID orderId, String projectTitle, List<OrderLineItemDetailResponse> lineItems) {

    public static InternalOrderSummaryResponse from(OrderSummarySnapshot snapshot) {
        return new InternalOrderSummaryResponse(snapshot.orderId(), snapshot.projectTitle(),
                snapshot.lineItems().stream().map(OrderLineItemDetailResponse::from).toList());
    }
}
