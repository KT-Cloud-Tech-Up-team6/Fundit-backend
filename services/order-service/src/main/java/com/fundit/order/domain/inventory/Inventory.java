package com.fundit.order.domain.inventory;

import lombok.Builder;
import lombok.Getter;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — 재고 차감/동기화에 낙관적 락 +
 * 조건부 UPDATE라는 동시성 불변식이 있다. 실제 증감은 이 객체를 로드→수정→save()하는
 * 방식(TOCTOU)으로 하지 않고, {@link InventoryRepository}의 조건부 UPDATE 메서드로만 한다 —
 * 그래서 이 클래스는 읽기 모델에 가깝게 불변으로 둔다.
 */
@Getter
@Builder(toBuilder = true)
public class Inventory {

    private final Long id;
    private final Long rewardId;
    private final int availableStock;
    private final int reservedStock;
    private final int initialQuantity;
    private final int version;

    /** ORDER-016 RewardCreated(isLimited=true) — 신규 재고 원장 행 생성. */
    public static Inventory createForLimitedReward(Long rewardId, int quantity) {
        return Inventory.builder()
                .rewardId(rewardId)
                .availableStock(quantity)
                .reservedStock(0)
                .initialQuantity(quantity)
                .version(0)
                .build();
    }

    public boolean hasEnoughStock(int quantity) {
        return availableStock >= quantity;
    }
}
