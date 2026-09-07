package com.fundit.project.infrastructure.inventory;

import com.fundit.project.application.reward.InventoryQueryClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** {@link InventoryQueryClient} 클래스 주석 참고 — order-service 연동 전 placeholder. */
@Component
public class NoopInventoryQueryClient implements InventoryQueryClient {

    @Override
    public Optional<Integer> getRemainingStock(Long rewardId) {
        return Optional.empty();
    }
}
