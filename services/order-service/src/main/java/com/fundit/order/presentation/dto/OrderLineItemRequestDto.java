package com.fundit.order.presentation.dto;

import com.fundit.order.application.order.OrderLineItemRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderLineItemRequestDto(
        @NotNull Long rewardId,
        @Min(1) int quantity,
        List<Long> optionValueIds
) {

    public OrderLineItemRequest toApplication() {
        return new OrderLineItemRequest(rewardId, quantity, optionValueIds);
    }
}
