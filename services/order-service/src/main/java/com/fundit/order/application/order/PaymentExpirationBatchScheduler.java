package com.fundit.order.application.order;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * ORDER-013 — payment_expires_at이 지난 PENDING 건을 찾아 만료 처리한다. 배치가 유일한
 * 전이 주체다(API 호출 경로에서는 즉석 만료 판정을 하지 않는다).
 */
@Component
@RequiredArgsConstructor
public class PaymentExpirationBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpirationBatchScheduler.class);

    private final FundingRepository fundingRepository;
    private final PaymentExpirationProcessor processor;

    @Scheduled(fixedDelayString = "${order.batch.payment-expiration.interval-ms:60000}")
    public void run() {
        List<Funding> targets = fundingRepository.findPendingExpiredBefore(Instant.now());
        int expiredCount = 0;
        for (Funding funding : targets) {
            try {
                if (processor.expireOne(funding.getId())) {
                    expiredCount++;
                }
            } catch (RuntimeException e) {
                log.error("미결제 주문 만료 처리 실패 — 다음 배치에서 재시도. fundingId={}", funding.getId(), e);
            }
        }
        if (expiredCount > 0) {
            log.info("미결제 주문 {}건을 만료 처리했습니다.", expiredCount);
        }
    }
}
