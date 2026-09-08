package com.fundit.order.application.order;

import java.util.List;

public record OrderLineItemRequest(Long rewardId, int quantity, List<Long> optionValueIds) {
}
