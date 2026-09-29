package com.fundit.payment.application.refund;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 토스 취소를 요청했지만 확정되지 않은 환불을 주기적으로 토스와 대조한다(#182). 토스 호출과 로컬 확정이
 * 트랜잭션으로 묶여 있지 않아, 취소는 성공했는데 확정 커밋이 실패하거나 응답이 불명확한(5xx·타임아웃) 건이
 * "취소 요청됨"으로 남는다. 결제 전 참여 취소에서 판단하지 못한 대기 결제(#181)도 같은 목록으로 들어온다.
 *
 * <p>방금 요청한 건은 호출이 아직 진행 중일 수 있어 {@code stale-after-minutes}가 지난 것만 본다.
 * ponytail: 다른 스케줄러처럼 인스턴스 1개 실행을 가정한다. 겹쳐 돌아도 취소 내역 유니크(transactionKey)와
 * 결제당 취소 요청 1건 인덱스가 이중 기록을 막지만, 레플리카를 늘리면 ShedLock으로 한 곳에서만 돌릴 것.
 */
@Component
@ConditionalOnProperty(prefix = "refund-cancel-reconcile", name = "worker-enabled", havingValue = "true",
        matchIfMissing = true)
public class RefundCancelReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefundCancelReconcileScheduler.class);

    private final RefundExecutionService refundExecutionService;
    private final int batchSize;
    private final Duration staleAfter;

    public RefundCancelReconcileScheduler(RefundExecutionService refundExecutionService,
                                          @Value("${refund-cancel-reconcile.batch-size:50}") int batchSize,
                                          @Value("${refund-cancel-reconcile.stale-after-minutes:5}") long staleAfterMinutes) {
        this.refundExecutionService = refundExecutionService;
        this.batchSize = batchSize;
        this.staleAfter = Duration.ofMinutes(staleAfterMinutes);
    }

    @Scheduled(fixedDelayString = "${refund-cancel-reconcile.poll-interval-ms:60000}")
    public void reconcile() {
        int resolved = refundExecutionService.reconcileCancelsRequestedBefore(Instant.now().minus(staleAfter), batchSize);
        if (resolved > 0) {
            log.info("확정되지 않은 환불 취소 {}건을 대사했습니다", resolved);
        }
    }
}
