package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.FundingLineItem;

import java.util.List;

public record OrderLineItemDetailResponse(
        Long rewardId, String rewardName, int quantity, long unitPrice, List<OrderLineItemOptionDetailResponse> options
) {

    public static OrderLineItemDetailResponse from(FundingLineItem lineItem) {
        return new OrderLineItemDetailResponse(lineItem.rewardId(), lineItem.rewardName(), lineItem.quantity(),
                lineItem.unitPrice(), lineItem.options().stream().map(OrderLineItemOptionDetailResponse::from).toList());
    }
}
