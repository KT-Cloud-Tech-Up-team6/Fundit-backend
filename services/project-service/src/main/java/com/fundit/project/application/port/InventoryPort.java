package com.fundit.project.application.port;

import java.util.List;
import java.util.Map;

/**
 * 재고는 order-service inventories가 source of truth다. 이 서비스는 재고를 저장하지 않고
 * 옵션 생명주기에 맞춰 order-service에 위임만 한다.
 * 구현체는 반드시 타임아웃을 설정하고, 실패는 DependencyFailureException으로 감싼다.
 */
public interface InventoryPort {

    void initialize(Long rewardOptionId, String sku, int initialStock);

    void deactivate(Long rewardOptionId);

    /** 옵션별 가용 재고. 조회에 실패한 옵션은 결과 맵에서 빠진다(응답에서는 null로 노출). */
    Map<Long, Integer> findAvailableStocks(List<Long> rewardOptionIds);
}
