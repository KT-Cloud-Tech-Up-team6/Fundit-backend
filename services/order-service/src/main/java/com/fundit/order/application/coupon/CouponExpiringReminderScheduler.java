package com.fundit.order.application.coupon;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 유효기간이 N일[기본 3일] 이내로 남은 AVAILABLE 쿠폰 발급 건에 만료임박 리마인더를 발행한다.
 * {@code CouponExpirationBatchScheduler}와 동일한 2단 구조(스케줄러가 대상 조회, 프로세서가
 * 건별 트랜잭션 처리)다.
 */
@Component
@RequiredArgsConstructor
public class CouponExpiringReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(CouponExpiringReminderScheduler.class);

    private final CouponIssuanceRepository couponIssuanceRepository;
    private final CouponExpiringReminderProcessor processor;

    @Value("${order.batch.coupon-expiring-reminder.window-days:3}")
    private long reminderWindowDays;

    @Scheduled(fixedDelayString = "${order.batch.coupon-expiring-reminder.interval-ms:3600000}")
    public void run() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(reminderWindowDays, ChronoUnit.DAYS);
        List<CouponIssuance> targets = couponIssuanceRepository.findAvailableExpiringWithin(now, windowEnd);
        int remindedCount = 0;
        for (CouponIssuance issuance : targets) {
            try {
                if (processor.remindOne(issuance.getId())) {
                    remindedCount++;
                }
            } catch (RuntimeException e) {
                log.error("쿠폰 만료임박 리마인더 발행 실패 — 다음 배치에서 재시도. couponIssuanceId={}", issuance.getId(), e);
            }
        }
        if (remindedCount > 0) {
            log.info("쿠폰 만료임박 리마인더 {}건을 발행했습니다.", remindedCount);
        }
    }
}
