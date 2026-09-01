package com.fundit.project.presentation.dto.reward;

/** sku와 재고는 여기서 바꿀 수 없다(재고는 order-service inventories 책임). */
public record RewardOptionUpdateRequest(String optionName) {
}
