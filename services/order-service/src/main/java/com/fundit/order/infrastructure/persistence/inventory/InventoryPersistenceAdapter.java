package com.fundit.order.infrastructure.persistence.inventory;

import com.fundit.order.domain.inventory.Inventory;
import com.fundit.order.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InventoryPersistenceAdapter implements InventoryRepository {

    private static final Logger log = LoggerFactory.getLogger(InventoryPersistenceAdapter.class);
    private static final int MAX_RETRY = 3;

    private final InventoryJpaRepository jpaRepository;
    private final InventoryMapper mapper;

    @Override
    public Optional<Inventory> findByRewardId(Long rewardId) {
        return jpaRepository.findByRewardId(rewardId).map(mapper::toDomain);
    }

    @Override
    public List<Inventory> findAllByRewardIdIn(List<Long> rewardIds) {
        return jpaRepository.findByRewardIdIn(rewardIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Inventory save(Inventory inventory) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(inventory)));
    }

    @Override
    public void deleteByRewardId(Long rewardId) {
        jpaRepository.deleteByRewardId(rewardId);
    }

    @Override
    public boolean decreaseStock(Long rewardId, int quantity) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Optional<InventoryJpaEntity> currentOpt = jpaRepository.findByRewardId(rewardId);
            if (currentOpt.isEmpty()) {
                return false;
            }
            InventoryJpaEntity current = currentOpt.get();
            if (current.getAvailableStock() < quantity) {
                return false;
            }
            int updated = jpaRepository.decreaseAvailableStock(rewardId, quantity, current.getVersion());
            if (updated > 0) {
                return true;
            }
            log.debug("재고 차감 버전 충돌, 재시도. rewardId={}, attempt={}", rewardId, attempt + 1);
        }
        log.warn("재고 차감 재시도 소진(동시 경쟁 심함). rewardId={}, quantity={}", rewardId, quantity);
        return false;
    }

    @Override
    public void increaseStock(Long rewardId, int quantity) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Optional<InventoryJpaEntity> currentOpt = jpaRepository.findByRewardId(rewardId);
            if (currentOpt.isEmpty()) {
                // 재고 원장 자체가 사라진 경우(예: 무제한 전환) — 원복할 대상이 없어 조용히 종료.
                log.warn("재고 원복 대상 없음(inventories 행 없음). rewardId={}", rewardId);
                return;
            }
            InventoryJpaEntity current = currentOpt.get();
            int updated = jpaRepository.increaseAvailableStock(rewardId, quantity, current.getVersion());
            if (updated > 0) {
                return;
            }
            log.debug("재고 원복 버전 충돌, 재시도. rewardId={}, attempt={}", rewardId, attempt + 1);
        }
        log.error("재고 원복 재시도 소진 — 운영 확인 필요. rewardId={}, quantity={}", rewardId, quantity);
    }

    @Override
    public InventoryDeltaResult applyQuantityDelta(Long rewardId, int delta, int newInitialQuantity) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            InventoryJpaEntity current = jpaRepository.findByRewardId(rewardId)
                    .orElseThrow(() -> new IllegalStateException("동기화 대상 inventories 행이 없습니다. rewardId=" + rewardId));
            boolean wouldClamp = current.getAvailableStock() + delta < 0;
            int updated = jpaRepository.applyQuantityDelta(rewardId, delta, newInitialQuantity, current.getVersion());
            if (updated > 0) {
                return new InventoryDeltaResult(wouldClamp);
            }
            log.debug("재고 동기화 버전 충돌, 재시도. rewardId={}, attempt={}", rewardId, attempt + 1);
        }
        throw new IllegalStateException("재고 동기화(delta 반영)에 반복적으로 실패했습니다. rewardId=" + rewardId);
    }
}
