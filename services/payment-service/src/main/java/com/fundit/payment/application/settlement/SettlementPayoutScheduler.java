package com.fundit.payment.application.settlement;

import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAYMENT-015 — 매주 금요일 정산 지급 실행. {@code status=PENDING}(이의신청 기간 경과) 배치만
 * 처리하고 {@code ON_HOLD}(PAYMENT-011 이의신청 접수)는 제외한다.
 *
 * <p>[범위 밖] 실제 계좌 지급(펌뱅킹/PG 이체 연동)은 지급 수단·정책이 미정이라(PaymentERD.md
 * 6장에 명시되지 않음) 이번 구현 범위 밖이다 — 상태 전이(PAID)까지만 수행한다.
 */
@Component
@RequiredArgsConstructor
public class SettlementPayoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementPayoutScheduler.class);

    private final SettlementBatchRepository settlementBatchRepository;

    @Scheduled(cron = "${settlement.payout.cron:0 0 4 * * FRI}")
    @Transactional
    public void run() {
        int paidCount = 0;
        for (SettlementBatch batch : settlementBatchRepository.findPayable()) {
            try {
                batch.markPaid();
                settlementBatchRepository.save(batch);
                paidCount++;
            } catch (RuntimeException e) {
                log.error("정산 지급 처리 실패 — PENDING으로 유지, 다음 주기에 재시도. batchId={}", batch.getId(), e);
            }
        }
        if (paidCount > 0) {
            log.info("정산 배치 {}건을 지급 완료 처리했습니다.", paidCount);
        }
    }
}
