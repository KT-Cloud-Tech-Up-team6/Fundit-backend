package com.fundit.order.application.inventory;

import com.fundit.order.domain.inventory.Inventory;
import com.fundit.order.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * project-service PROJECT-028이 잔여재고를 실시간으로 동기 조회하는 용도(CLAUDE.md
 * "project-service용 재고 조회는 동기 API로" 참고).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;

    /** 리워드가 무제한이거나 재고 원장이 아직 없으면 빈 값 — 컨트롤러는 remainingStock=null로 응답한다. */
    public Optional<Integer> getRemainingStock(Long rewardId) {
        return inventoryRepository.findByRewardId(rewardId).map(Inventory::getAvailableStock);
    }
}
