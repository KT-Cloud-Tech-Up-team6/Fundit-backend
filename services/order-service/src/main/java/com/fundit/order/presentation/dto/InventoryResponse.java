package com.fundit.order.presentation.dto;

/** remainingStock이 null이면 무제한이거나 재고 원장이 아직 없다는 뜻이다. */
public record InventoryResponse(Long rewardId, Integer remainingStock) {
}
