package com.fundit.order.infrastructure.persistence.inventory;

import com.fundit.order.domain.inventory.Inventory;
import com.fundit.order.domain.inventory.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * inventories 조건부 UPDATE(낙관적 락)가 실제 Postgres에서 의도대로 동작하는지 검증한다.
 * 동시성(경쟁 상황) 검증은 별도 InventoryStockDecreaseConcurrencyTest에서 다룬다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class InventoryPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private InventoryJpaRepository jpaRepository;

    private Long seed(Long rewardId, int availableStock, int initialQuantity) {
        return jpaRepository.save(InventoryJpaEntity.builder()
                .rewardId(rewardId)
                .availableStock(availableStock)
                .reservedStock(0)
                .initialQuantity(initialQuantity)
                .version(0)
                .build()).getId();
    }

    @Test
    void 재고가_충분하면_차감에_성공하고_버전이_증가한다() {
        // given
        seed(1L, 10, 10);

        // when
        boolean result = inventoryRepository.decreaseStock(1L, 3);

        // then
        assertThat(result).isTrue();
        Inventory updated = inventoryRepository.findByRewardId(1L).orElseThrow();
        assertThat(updated.getAvailableStock()).isEqualTo(7);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    void 재고가_부족하면_차감에_실패하고_재고는_그대로다() {
        // given
        seed(2L, 2, 10);

        // when
        boolean result = inventoryRepository.decreaseStock(2L, 3);

        // then
        assertThat(result).isFalse();
        assertThat(inventoryRepository.findByRewardId(2L).orElseThrow().getAvailableStock()).isEqualTo(2);
    }

    @Test
    void 원복하면_재고가_증가한다() {
        // given
        seed(3L, 5, 10);

        // when
        inventoryRepository.increaseStock(3L, 4);

        // then
        assertThat(inventoryRepository.findByRewardId(3L).orElseThrow().getAvailableStock()).isEqualTo(9);
    }

    @Test
    void 델타를_반영하면_가용재고와_초기수량이_함께_갱신된다() {
        // given — 100개 중 40개 판매(availableStock=60), 150개로 증가(delta=50)
        seed(4L, 60, 100);

        // when
        InventoryRepository.InventoryDeltaResult result = inventoryRepository.applyQuantityDelta(4L, 50, 150);

        // then
        assertThat(result.clamped()).isFalse();
        Inventory updated = inventoryRepository.findByRewardId(4L).orElseThrow();
        assertThat(updated.getAvailableStock()).isEqualTo(110);
        assertThat(updated.getInitialQuantity()).isEqualTo(150);
    }

    @Test
    void 델타반영으로_0미만이_되면_0으로_클램프되고_결과에_표시된다() {
        // given — 100개 중 90개 판매(availableStock=10), 5개로 감소(delta=-95)
        seed(5L, 10, 100);

        // when
        InventoryRepository.InventoryDeltaResult result = inventoryRepository.applyQuantityDelta(5L, -95, 5);

        // then
        assertThat(result.clamped()).isTrue();
        assertThat(inventoryRepository.findByRewardId(5L).orElseThrow().getAvailableStock()).isEqualTo(0);
    }

    @Test
    void 무제한_전환시_행을_삭제하면_더이상_조회되지_않는다() {
        // given
        seed(6L, 10, 10);

        // when
        inventoryRepository.deleteByRewardId(6L);

        // then
        Optional<Inventory> found = inventoryRepository.findByRewardId(6L);
        assertThat(found).isEmpty();
    }
}
