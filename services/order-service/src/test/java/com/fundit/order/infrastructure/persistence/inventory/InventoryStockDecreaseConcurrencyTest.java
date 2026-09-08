package com.fundit.order.infrastructure.persistence.inventory;

import com.fundit.order.domain.inventory.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * test-convention.md — 재고 차감처럼 동시성 이슈가 있는 로직은 별도 ConcurrencyTest로 검증한다.
 * 재고 10개에 20개의 동시 차감 요청이 몰려도 정확히 10건만 성공(오버셀 없음)해야 한다.
 * 각 요청이 별도 트랜잭션/스레드에서 실제로 경쟁해야 하므로 @Transactional을 붙이지 않는다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InventoryStockDecreaseConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    private static final Long REWARD_ID = 999L;
    private static final int INITIAL_STOCK = 10;
    private static final int CONCURRENT_REQUESTS = 20;

    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private InventoryJpaRepository jpaRepository;

    @Test
    void 동시에_요청해도_재고보다_많이_차감되지_않는다() throws InterruptedException {
        // given
        jpaRepository.deleteAll();
        jpaRepository.save(InventoryJpaEntity.builder()
                .rewardId(REWARD_ID)
                .availableStock(INITIAL_STOCK)
                .reservedStock(0)
                .initialQuantity(INITIAL_STOCK)
                .version(0)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    boolean success = inventoryRepository.decreaseStock(REWARD_ID, 1);
                    if (success) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);
        assertThat(failCount.get()).isEqualTo(CONCURRENT_REQUESTS - INITIAL_STOCK);
        assertThat(inventoryRepository.findByRewardId(REWARD_ID).orElseThrow().getAvailableStock()).isEqualTo(0);
    }
}
