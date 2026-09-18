package com.fundit.project.infrastructure.inventory;

import com.fundit.project.application.reward.InventoryQueryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * order-service 연동을 끄고 싶을 때(로컬 개발 등) 쓰는 스텁 — 항상 빈 값(조회 불가)을 반환한다.
 * {@link InventoryQueryClient} 클래스 주석 참고. 실제 order-service 호출은
 * {@code order.integration.inventory-client.mode=http}로 전환한 {@link HttpInventoryQueryClient}가 담당한다.
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.inventory-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
public class StubInventoryQueryClient implements InventoryQueryClient {

    private static final Logger log = LoggerFactory.getLogger(StubInventoryQueryClient.class);

    @Override
    public Optional<Integer> getRemainingStock(Long rewardId) {
        log.debug("[STUB] order-service 재고 연동 비활성화 — 빈 값으로 대체합니다. rewardId={}", rewardId);
        return Optional.empty();
    }
}
