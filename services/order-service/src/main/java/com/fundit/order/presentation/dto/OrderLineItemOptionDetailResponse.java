package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.FundingLineItemOption;

public record OrderLineItemOptionDetailResponse(String optionGroupName, String optionValue) {

    public static OrderLineItemOptionDetailResponse from(FundingLineItemOption option) {
        return new OrderLineItemOptionDetailResponse(option.optionGroupName(), option.optionValue());
    }
}
