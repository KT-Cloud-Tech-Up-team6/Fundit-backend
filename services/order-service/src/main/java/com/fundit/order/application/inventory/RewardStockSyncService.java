package com.fundit.order.application.inventory;

import com.fundit.order.domain.inventory.Inventory;
import com.fundit.order.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * ORDER-016 — project-service RewardCreated/RewardUpdated 이벤트로 재고 원장(inventories)을
 * 동기화한다. RewardUpdated는 절대값 덮어쓰기가 아니라 initial_quantity 대비 델타만 반영한다
 * (order-service CLAUDE.md "핵심 설계 결정" — 이미 판매된 수량이 부활하는 버그를 막기 위함).
 */
@Service
@RequiredArgsConstructor
public class RewardStockSyncService implements RewardEventListener {

    private static final Logger log = LoggerFactory.getLogger(RewardStockSyncService.class);

    private final InventoryRepository inventoryRepository;

    @Override
    @Transactional
    public void onRewardCreated(RewardCreatedEvent event) {
        if (!event.isLimited()) {
            // isLimited=false인 리워드는 이 테이블에 행을 두지 않는다.
            return;
        }
        int quantity = requireQuantity(event.rewardId(), event.quantity());
        Optional<Inventory> existing = inventoryRepository.findByRewardId(event.rewardId());
        if (existing.isEmpty()) {
            inventoryRepository.save(Inventory.createForLimitedReward(event.rewardId(), quantity));
            return;
        }
        // 재시도로 같은 RewardCreated가 다시 들어온 경우 — upsert로 멱등 처리.
        syncToQuantity(existing.get(), quantity);
    }

    @Override
    @Transactional
    public void onRewardUpdated(RewardUpdatedEvent event) {
        Optional<Inventory> existing = inventoryRepository.findByRewardId(event.rewardId());

        if (!event.isLimited()) {
            // 무제한 전환 — 해당 inventories 행 삭제.
            if (existing.isPresent()) {
                inventoryRepository.deleteByRewardId(event.rewardId());
            }
            return;
        }

        int quantity = requireQuantity(event.rewardId(), event.quantity());
        if (existing.isEmpty()) {
            // 방어적 처리: RewardCreated 없이 isLimited=true RewardUpdated만 도착한 경우.
            inventoryRepository.save(Inventory.createForLimitedReward(event.rewardId(), quantity));
            return;
        }
        syncToQuantity(existing.get(), quantity);
    }

    private void syncToQuantity(Inventory current, int newQuantity) {
        int delta = newQuantity - current.getInitialQuantity();
        InventoryRepository.InventoryDeltaResult result =
                inventoryRepository.applyQuantityDelta(current.getRewardId(), delta, newQuantity);
        if (result.clamped()) {
            log.warn("재고 델타 반영 중 available_stock이 0 미만이 되어 0으로 클램프했습니다. "
                            + "이미 판매된 수량보다 적게 재설정하려는 시도일 수 있어 운영 확인이 필요합니다. "
                            + "rewardId={}, delta={}",
                    current.getRewardId(), delta);
        }
    }

    private int requireQuantity(Long rewardId, Integer quantity) {
        if (quantity == null) {
            throw new IllegalStateException("isLimited=true인데 quantity가 없는 리워드 이벤트입니다. rewardId=" + rewardId);
        }
        return quantity;
    }
}
