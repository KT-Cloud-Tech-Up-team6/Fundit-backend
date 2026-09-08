package com.fundit.order.domain.funding;

/** 주문 시점 옵션 스냅샷(옵션 그룹명/값은 project-service가 나중에 바꿔도 과거 주문은 불변). */
public record FundingLineItemOption(
        Long id,
        Long optionGroupId,
        String optionGroupName,
        Long optionValueId,
        String optionValue
) {
}
