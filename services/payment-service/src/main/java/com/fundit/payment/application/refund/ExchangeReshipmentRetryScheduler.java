package com.fundit.payment.application.refund;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 교환비 결제까지 끝났는데 fulfillment 재발송 요청이 성공하지 못한 건을 주기적으로 다시 보낸다.
 * 결제가 이미 승인된 뒤라 요청 유실을 로그로 끝낼 수 없어 둔 장치다 — 요청은 refundRequestId로
 * 멱등이므로 재시도가 재발송을 두 번 만들지 않는다.
 *
 * <p>아웃박스 워커({@code PaymentEventOutboxWorker})와 같은 역할이지만 별도 테이블을 두지 않는다:
 * {@code refund_requests}의 (EXCHANGE, PROCESSING, reshipment_requested_at IS NULL) 행이 곧 작업
 * 목록이다(V10).
 */
@Component
@ConditionalOnProperty(prefix = "exchange-reshipment-retry", name = "worker-enabled", havingValue = "true",
        matchIfMissing = true)
public class ExchangeReshipmentRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExchangeReshipmentRetryScheduler.class);

    private final ExchangeService exchangeService;
    private final int batchSize;

    public ExchangeReshipmentRetryScheduler(ExchangeService exchangeService,
                                            @Value("${exchange-reshipment-retry.batch-size:50}") int batchSize) {
        this.exchangeService = exchangeService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${exchange-reshipment-retry.poll-interval-ms:60000}")
    public void retryPending() {
        int requested = exchangeService.retryPendingReshipmentRequests(batchSize);
        if (requested > 0) {
            log.info("교환 재발송 요청 재시도 성공 {}건", requested);
        }
    }
}
