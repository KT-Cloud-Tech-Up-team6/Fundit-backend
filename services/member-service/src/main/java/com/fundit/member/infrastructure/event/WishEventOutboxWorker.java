package com.fundit.member.infrastructure.event;

import com.fundit.member.infrastructure.event.WishEventTransport.WishEvent;
import com.fundit.member.infrastructure.persistence.event.WishEventOutboxJpaEntity;
import com.fundit.member.infrastructure.persistence.event.WishEventOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미발행 아웃박스 행을 꺼내 {@link WishEventTransport}로 전달한다. 실패하면 published_at을
 * 채우지 않고 attempt_count만 올려 다음 주기에 재시도한다(project-service
 * {@code RewardEventOutboxWorker}와 같은 형태).
 *
 * <p>{@code @Scheduled}가 실제로 돌려면 {@code MemberServiceApplication}에 {@code @EnableScheduling}이
 * 있어야 한다 — 없으면 예외 없이 조용히 안 돌고 이벤트만 영영 쌓인다.
 */
@Component
@ConditionalOnProperty(prefix = "wish-event-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class WishEventOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(WishEventOutboxWorker.class);

    private final WishEventOutboxJpaRepository outboxRepository;
    private final WishEventTransport transport;
    private final int batchSize;

    public WishEventOutboxWorker(WishEventOutboxJpaRepository outboxRepository,
                                 WishEventTransport transport,
                                 @Value("${wish-event-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${wish-event-outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (WishEventOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                deliver(event);
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("찜 이벤트 발행 실패, 재시도 예정. id={} type={}", event.getId(), event.getEventType(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }

    private void deliver(WishEventOutboxJpaEntity event) {
        WishEvent payload = new WishEvent("member:" + event.getId(), event.getMemberId(), event.getProjectId());
        if (WishEventOutboxJpaEntity.TYPE_WISHED.equals(event.getEventType())) {
            transport.sendWished(payload);
            return;
        }
        if (WishEventOutboxJpaEntity.TYPE_UNWISHED.equals(event.getEventType())) {
            transport.sendUnwished(payload);
            return;
        }
        throw new IllegalStateException("알 수 없는 찜 이벤트 타입: " + event.getEventType());
    }
}
