package com.fundit.notification.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.notification.application.notification.NotificationEventListener;
import com.fundit.notification.application.notification.NotificationEventListener.NotificationRaisedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * NOTI-006 — {@code notification.raised.v1} 구독 어댑터.
 *
 * <p>인바운드 포트({@link NotificationEventListener})를 호출만 하는 얇은 어댑터다.
 * 수신설정 확인·멱등 같은 적재 규칙은 포트 구현체에 있고 여기 두지 않는다
 * (order-service {@code RewardEventListener}가 정의해 둔 "포트 + 어댑터" 구조를 따른다).
 *
 * <p><b>JsonDeserializer 대신 String으로 받는 이유</b>: 역직렬화를 리스너 안에서 하면
 * 실패한 메시지의 원문이 그대로 보이고, trusted packages·타입 헤더 설정도 필요 없다.
 * 서비스가 이미 가진 ObjectMapper(Jackson 3)를 그대로 쓴다.
 *
 * <p><b>예외를 잡지 않는 이유</b>: NOTI-006이 요구하는 "실패한 메시지만 건너뛰고 컨슈머는 계속 진행"은
 * Spring Kafka의 기본 {@code DefaultErrorHandler}가 이미 한다(재시도 후 오프셋을 넘기고 다음 레코드로 간다).
 * 여기 try-catch를 두면 프레임워크가 하는 일을 손으로 다시 하는 것이라 뺐다 —
 * 실제로 try-catch가 있든 없든 {@code NotificationKafkaListenerIntegrationTest}의
 * "깨진 메시지가 와도 컨슈머가 멈추지 않는다"가 동일하게 통과하는 것을 확인했다.
 * 그 테스트는 이제 이 프레임워크 동작을 고정하는 역할을 한다 — 에러 핸들러 설정을 바꾸면 깨진다.
 *
 * <p>토픽명은 {@link KafkaTopics} 상수를 쓴다 — 리터럴로 들고 있으면 발행 측과 어긋나도
 * 예외가 나지 않고 조용히 메시지만 안 온다({@code .claude/rules/event-convention.md}).
 */
@Component
@RequiredArgsConstructor
public class NotificationKafkaListener {

    private final NotificationEventListener notificationEventListener;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_RAISED)
    public void onMessage(String payload) {
        notificationEventListener.onNotificationRaised(
                objectMapper.readValue(payload, NotificationRaisedEvent.class));
    }
}
