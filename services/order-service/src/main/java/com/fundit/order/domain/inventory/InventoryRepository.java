package com.fundit.order.domain.inventory;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {

    Optional<Inventory> findByRewardId(Long rewardId);

    List<Inventory> findAllByRewardIdIn(List<Long> rewardIds);

    Inventory save(Inventory inventory);

    void deleteByRewardId(Long rewardId);

    /**
     * 조건부 UPDATE로 재고를 원자적으로 차감한다(낙관적 락 + 짧은 재시도, ORDER-003/CLAUDE.md
     * "재고 차감은 낙관적 락 + 조건부 UPDATE로만 한다").
     *
     * @return true면 차감 성공, false면 (재시도 후에도) 재고 부족으로 최종 실패
     */
    boolean decreaseStock(Long rewardId, int quantity);

    /**
     * 참여 취소(ORDER-014)/미결제 만료(ORDER-013)로 차감했던 재고를 원복한다.
     * 재고를 늘리는 방향이라 "부족" 실패 케이스는 없고 버전 충돌만 재시도한다.
     */
    void increaseStock(Long rewardId, int quantity);

    /**
     * ORDER-016 RewardUpdated 전용 — 절대값 덮어쓰기가 아니라 델타(delta)만 available_stock에
     * 반영하고 initial_quantity를 새 값으로 갱신한다. 결과가 0 미만이 되면 0으로 클램프한다.
     */
    InventoryDeltaResult applyQuantityDelta(Long rewardId, int delta, int newInitialQuantity);

    record InventoryDeltaResult(boolean clamped) {
    }
}
