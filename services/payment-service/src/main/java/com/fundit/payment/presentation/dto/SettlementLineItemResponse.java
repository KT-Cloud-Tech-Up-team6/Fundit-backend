package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.settlement.OrderSettlementAggregateClient.LineItemAggregate;

public record SettlementLineItemResponse(Long rewardId, String rewardName, String optionName, int quantity,
                                          long amount) {

    public static SettlementLineItemResponse from(LineItemAggregate aggregate) {
        return new SettlementLineItemResponse(aggregate.rewardId(), aggregate.rewardName(), aggregate.optionName(),
                aggregate.quantity(), aggregate.amount());
    }
}
