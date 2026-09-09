package com.fundit.order.infrastructure.persistence.coupon;

import com.fundit.order.domain.coupon.CouponRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * test-convention.md — LIVE 방송 중 선착순 쿠폰처럼 순간적으로 요청이 몰리는 조건부 UPDATE는
 * 별도 ConcurrencyTest로 검증한다(CLAUDE.md "쿠폰 재고 차감도 재고와 동일하게..."). 쿠폰 5장에
 * 20개의 동시 클레임 요청이 몰려도 정확히 5건만 성공(초과발급 없음)해야 한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CouponRemainingQuantityConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    private static final String COUPON_CODE = "LIVE-CONCURRENCY";
    private static final int INITIAL_QUANTITY = 5;
    private static final int CONCURRENT_REQUESTS = 20;

    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private CouponJpaRepository jpaRepository;

    @Test
    void 동시에_요청해도_수량보다_많이_발급되지_않는다() throws InterruptedException {
        // given
        jpaRepository.deleteAll();
        jpaRepository.save(CouponJpaEntity.builder()
                .couponCode(COUPON_CODE).couponName("선착순 쿠폰")
                .discountType("AMOUNT").discountValue(1_000)
                .issuerType("PLATFORM").targetScope("ALL")
                .minFundingAmount(0).perMemberLimit(1).remainingQuantity(INITIAL_QUANTITY)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).issueChannel("LIVE")
                .version(0).build());

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger();

        // when
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    if (couponRepository.decreaseRemainingQuantity(COUPON_CODE)) {
                        successCount.incrementAndGet();
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
        assertThat(successCount.get()).isEqualTo(INITIAL_QUANTITY);
        assertThat(couponRepository.findByCouponCode(COUPON_CODE).orElseThrow().getRemainingQuantity()).isZero();
    }
}
