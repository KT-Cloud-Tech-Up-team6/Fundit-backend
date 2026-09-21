package com.fundit.live.infrastructure.event;

import com.fundit.live.infrastructure.event.LiveEventTransport.LiveEndedEvent;
import com.fundit.live.infrastructure.event.LiveEventTransport.LiveStartedEvent;
import com.fundit.live.infrastructure.event.LiveEventTransport.QuestionsSummarizedEvent;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaEntity;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 미발행 아웃박스 행을 꺼내 발행한다. 실패하면 {@code published_at}을 채우지 않고
 * {@code attempt_count}만 올려 다음 주기에 재시도한다.
 *
 * <p>{@code @Scheduled}가 실제로 돌려면 {@code LiveServiceApplication}에 {@code @EnableScheduling}이
 * 있어야 한다 — 없으면 예외 없이 조용히 안 돌고 이벤트만 영영 쌓인다.
 */
@Component
@ConditionalOnProperty(prefix = "live.outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class LiveEventOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(LiveEventOutboxWorker.class);
    private static final String SERVICE_NAME = "live";

    private final LiveEventOutboxJpaRepository outboxRepository;
    private final LiveEventTransport transport;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final int batchSize;

    public LiveEventOutboxWorker(LiveEventOutboxJpaRepository outboxRepository,
                                 LiveEventTransport transport,
                                 @Value("${live.outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${live.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (LiveEventOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                deliver(event);
                // deliver()가 전송 확인까지 끝낸 뒤에만 여기 온다.
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("이벤트 발행 실패, 재시도 예정. id={} type={}", event.getId(), event.getEventType(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }

    private void deliver(LiveEventOutboxJpaEntity event) {
        String eventId = SERVICE_NAME + ":" + event.getId();
        Payload payload = jsonMapper.readValue(event.getPayload(), Payload.class);
        switch (event.getEventType()) {
            case LiveEventOutboxJpaEntity.TYPE_LIVE_ENDED -> transport.sendLiveEnded(
                    new LiveEndedEvent(eventId, payload.liveId(), payload.projectId(), payload.occurredAt()));
            case LiveEventOutboxJpaEntity.TYPE_QUESTIONS_SUMMARIZED -> transport.sendQuestionsSummarized(
                    new QuestionsSummarizedEvent(eventId, payload.liveId(), payload.projectId(),
                            payload.summaries()));
            case LiveEventOutboxJpaEntity.TYPE_LIVE_STARTED -> transport.sendLiveStarted(
                    new LiveStartedEvent(eventId, payload.liveId(), payload.projectId(), payload.occurredAt()));
            default -> throw new IllegalStateException("알 수 없는 이벤트 타입: " + event.getEventType());
        }
    }

    /**
     * 아웃박스 payload의 공통 형태. 질문요약만 summaries를 쓰고 나머지는 null이다 —
     * 종류별 레코드를 3개 두면 역직렬화 분기가 타입마다 생긴다.
     */
    private record Payload(String liveId, String projectId, String occurredAt,
                           List<QuestionsSummarizedEvent.SummaryItem> summaries) {
    }
}
