package com.fundit.project.infrastructure.external;

import com.fundit.project.application.port.InventoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * order-service가 아직 없어서 두는 임시 어댑터. 호출 사실만 남기고 아무 것도 하지 않는다.
 * <p>
 * order-service 착수 시 RestClient 기반 어댑터로 교체하고 이 클래스는 지운다. 그때
 * 반드시 연결/응답 타임아웃을 명시하고(프레임워크 기본값 금지, 루트 CLAUDE.md),
 * 실패는 DependencyFailureException으로 감싸 503으로 응답해야 한다.
 */
@Slf4j
@Component
public class StubInventoryAdapter implements InventoryPort {

    @Override
    public void initialize(Long rewardOptionId, String sku, int initialStock) {
        log.warn("재고 초기화 위임 생략 - order-service 미연동. optionId={}, sku={}, initialStock={}",
                rewardOptionId, sku, initialStock);
    }

    @Override
    public void deactivate(Long rewardOptionId) {
        log.warn("재고 비활성화 위임 생략 - order-service 미연동. optionId={}", rewardOptionId);
    }

    @Override
    public Map<Long, Integer> findAvailableStocks(List<Long> rewardOptionIds) {
        return Map.of();
    }
}
