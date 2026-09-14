package com.fundit.fulfillment.application.tracker;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.StaleUpdateReminderEvent;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * FULFILLMENT-004 — {@code current_stage <> 'DELIVERY'}이고 {@code last_updated_at}이
 * N일[정책값, 초안 7일] 이상 경과한(한 번도 갱신되지 않은 경우 포함) 트래커에 판매자 업데이트
 * 요청 알림을 발행한다. 알림 발행은 outbox insert 한 건이라 별도 트랜잭션 분리 없이 항목별로
 * 예외만 잡는다(order-service {@code PaymentExpirationBatchScheduler}와 동일한 방어 스타일).
 */
@Component
public class StaleUpdateNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaleUpdateNotificationScheduler.class);

    private final FulfillmentTrackerRepository trackerRepository;
    private final FulfillmentNotificationPublisher notificationPublisher;
    private final long staleUpdateThresholdDays;

    public StaleUpdateNotificationScheduler(FulfillmentTrackerRepository trackerRepository,
                                             FulfillmentNotificationPublisher notificationPublisher,
                                             @Value("${fulfillment.stale-update-threshold-days:7}") long staleUpdateThresholdDays) {
        this.trackerRepository = trackerRepository;
        this.notificationPublisher = notificationPublisher;
        this.staleUpdateThresholdDays = staleUpdateThresholdDays;
    }

    @Scheduled(fixedDelayString = "${fulfillment.stale-update-notification.interval-ms:3600000}")
    public void run() {
        Instant threshold = Instant.now().minus(staleUpdateThresholdDays, ChronoUnit.DAYS);
        List<FulfillmentTracker> targets = trackerRepository.findStale(threshold);
        int notified = 0;
        for (FulfillmentTracker tracker : targets) {
            try {
                notificationPublisher.publishStaleUpdateReminder(new StaleUpdateReminderEvent(tracker.getProjectId()));
                notified++;
            } catch (RuntimeException e) {
                log.error("미등록 알림 발행 실패 — 다음 배치에서 재시도. projectId={}", tracker.getProjectId(), e);
            }
        }
        if (notified > 0) {
            log.info("미등록 알림 {}건을 발행했습니다.", notified);
        }
    }
}
