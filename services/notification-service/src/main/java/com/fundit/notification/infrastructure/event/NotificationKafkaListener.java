package com.fundit.notification.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.notification.application.notification.NotificationEventListener;
import com.fundit.notification.application.notification.NotificationEventListener.LiveStartedEvent;
import com.fundit.notification.application.notification.NotificationEventListener.NotificationRaisedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * NOTI-006 — {@code notification.raised.v1} / NOTI-002 — {@code live.started.v1} 구독 어댑터.
 *
 * <p>인바운드 포트({@link NotificationEventListener})로 위임만 하는 얇은 어댑터다.
 * 수신설정 확인·멱등 같은 적재 규칙은 포트 구현체에 있고 여기 두지 않는다
 * (order-service {@code RewardEventKafkaListener}와 같은 형태).
 *
 * <p>JSON → 레코드 변환은 {@link KafkaConsumerConfig}의 메시지 컨버터가 파라미터 타입으로
 * 추론해서 한다. 역직렬화·처리 실패는 같은 설정의 에러 핸들러가 로그만 남기고 다음 메시지로
 * 넘긴다 — 한 건 때문에 파티션이 멈추지 않는다(event-convention.md 7번).
 *
 * <p>토픽명은 {@link KafkaTopics} 상수를 쓴다 — 리터럴로 들고 있으면 발행 측과 어긋나도
 * 예외가 나지 않고 조용히 메시지만 안 온다({@code .claude/rules/event-convention.md}).
 */
@Component
@RequiredArgsConstructor
public class NotificationKafkaListener {

    private final NotificationEventListener notificationEventListener;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_RAISED)
    public void onNotificationRaised(NotificationRaisedEvent event) {
        notificationEventListener.onNotificationRaised(event);
    }

    /** NOTI-002 — {@code live.started.v1} 구독 어댑터. */
    @KafkaListener(topics = KafkaTopics.LIVE_STARTED)
    public void onLiveStarted(LiveStartedEvent event) {
        notificationEventListener.onLiveStarted(event);
    }
}
