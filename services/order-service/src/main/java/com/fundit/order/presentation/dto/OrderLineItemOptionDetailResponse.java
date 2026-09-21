package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.FundingLineItemOption;

public record OrderLineItemOptionDetailResponse(Long optionValueId, String optionGroupName, String optionValue) {

    public static OrderLineItemOptionDetailResponse from(FundingLineItemOption option) {
        return new OrderLineItemOptionDetailResponse(option.optionValueId(), option.optionGroupName(),
                option.optionValue());
    }
}
