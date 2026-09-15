package com.fundit.order.application.coupon;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 유효기간이 지난 AVAILABLE 쿠폰 발급 건을 EXPIRED로 전이한다(ORDER-009 쿠폰함의
 * {@code status=EXPIRED} 필터가 실제 값을 갖도록). order-service
 * {@code PaymentExpirationBatchScheduler}와 동일한 2단 구조(스케줄러가 대상 조회,
 * 프로세서가 건별 트랜잭션 처리)다.
 */
@Component
@RequiredArgsConstructor
public class CouponExpirationBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponExpirationBatchScheduler.class);

    private final CouponIssuanceRepository couponIssuanceRepository;
    private final CouponExpirationProcessor processor;

    @Scheduled(fixedDelayString = "${order.batch.coupon-expiration.interval-ms:3600000}")
    public void run() {
        List<CouponIssuance> targets = couponIssuanceRepository.findAvailableExpired(Instant.now());
        int expiredCount = 0;
        for (CouponIssuance issuance : targets) {
            try {
                if (processor.expireOne(issuance.getId())) {
                    expiredCount++;
                }
            } catch (RuntimeException e) {
                log.error("쿠폰 만료 처리 실패 — 다음 배치에서 재시도. couponIssuanceId={}", issuance.getId(), e);
            }
        }
        if (expiredCount > 0) {
            log.info("쿠폰 발급 {}건을 만료 처리했습니다.", expiredCount);
        }
    }
}
