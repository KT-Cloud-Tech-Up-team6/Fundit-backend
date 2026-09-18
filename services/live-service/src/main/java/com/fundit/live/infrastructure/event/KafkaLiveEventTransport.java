package com.fundit.live.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.common.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * payload는 봉투 없이 평평한 JSON이다 — 레코드가 그대로 직렬화되고 {@code eventId}는
 * 그 필드 중 하나다(event-convention.md 4·5번).
 *
 * <p>파티션 키는 방송 이벤트가 {@code liveId}, 알림이 {@code memberId}다.
 * 알림은 한 회원 기준 순서가 중요하고, 방송 자산은 한 방송 기준 순서가 중요하다.
 */
@Component
@RequiredArgsConstructor
public class KafkaLiveEventTransport implements LiveEventTransport {

    /** 아웃박스 워커가 다음 주기에 재시도하므로 길게 잡을 이유가 없다. */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendLiveEnded(LiveEndedEvent event) {
        send(KafkaTopics.LIVE_ENDED, event.liveId(), event);
    }

    @Override
    public void sendQuestionsSummarized(QuestionsSummarizedEvent event) {
        send(KafkaTopics.LIVE_QUESTIONS_SUMMARIZED, event.liveId(), event);
    }

    @Override
    public void sendLiveStarted(LiveStartedEvent event) {
        send(KafkaTopics.LIVE_STARTED, event.liveId(), event);
    }

    /**
     * <b>전송 결과를 반드시 기다린다.</b> {@code send()}는 결과를 미래에 채우는 비동기 호출이라
     * 그냥 반환하면 브로커가 죽어 있어도 워커가 성공으로 보고 {@code published_at}을 채운다 —
     * 행이 발행된 척 사라지고 아웃박스를 둔 이유가 통째로 무력화된다
     * (member/payment에서 실제로 고친 버그다).
     */
    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // 인터럽트를 삼키면 상위(스케줄러 종료 등)가 중단 신호를 영영 못 본다.
            Thread.currentThread().interrupt();
            throw new DependencyFailureException(e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new DependencyFailureException(e);
        }
    }
}
