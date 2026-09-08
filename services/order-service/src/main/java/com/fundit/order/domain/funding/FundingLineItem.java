package com.fundit.order.domain.funding;

import java.util.List;

/** 주문 시점 리워드 이름/단가 스냅샷(project-service가 나중에 값을 바꿔도 과거 주문은 불변). */
public record FundingLineItem(
        Long id,
        Long rewardId,
        String rewardName,
        int quantity,
        long unitPrice,
        List<FundingLineItemOption> options
) {

    public long amount() {
        return unitPrice * quantity;
    }
}
