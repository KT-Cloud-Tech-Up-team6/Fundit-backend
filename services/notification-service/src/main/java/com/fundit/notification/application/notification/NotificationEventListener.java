package com.fundit.notification.application.notification;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;

import java.util.UUID;

/**
 * 타 서비스가 발행한 알림 이벤트를 받아 알림함에 적재하는 인바운드 포트(NOTI-006).
 * <b>알림을 만드는 유일한 입구다</b> — 알림을 생성하는 REST 엔드포인트는 의도적으로 두지 않았다.
 *
 * <p>수신자(memberId)가 <b>이미 채워진 이벤트만</b> 받는다. fundingId/projectId로 참여자·찜한 회원을
 * 되물으면 알림 도메인이 주문·회원 도메인에 동기 의존하게 되고, 그 도메인이 바뀔 때마다 알림이 함께 흔들린다.
 * 이벤트에 memberId가 채워져 오지 않으면 그건 발행 측이 고칠 일이다.
 *
 * <p>메시지 브로커 어댑터는 아직 없다 — order-service의 RewardEventListener와 같은 상태다.
 * Kafka로 확정(2026-09-11)됐지만 레포에 spring-kafka 의존성도, 발행자도, 토픽명 규약도 아직 없고
 * (docs/ci-workflow-guide.md: "확정 전까지는 넣지 않습니다"), 무엇보다 발행 측 이벤트 레코드에
 * <b>memberId와 eventId가 둘 다 빠져 있다</b>(FundingSucceededEvent(fundingId, projectId) 등).
 * notifications.event_id가 NOT NULL이라 이게 합의되기 전엔 알림이 한 건도 쌓이지 않는다
 * (NotificationFunctionalSpec.md "미확정/보류 사항" 🔴 2건, 협의처: order·payment 담당자).
 * 계약이 정해지면 infrastructure/event에 @KafkaListener 어댑터만 추가해 이 포트를 호출하면 된다.
 */
public interface NotificationEventListener {

    void onNotificationRaised(NotificationRaisedEvent event);

    /**
     * @param eventId    발행 측이 싣는 이벤트 고유 ID. 멱등의 유일한 근거이므로 비어 있으면 안 된다.
     *                   임시로 Kafka 좌표(topic-partition-offset)를 쓸 수는 있으나, 재발행 시 값이 달라져
     *                   중복 차단이 되지 않는다는 걸 알고 써야 한다.
     * @param memberId   수신자. 발행 측이 채워 보낸다.
     * @param title      알림함 노출 문구(100자 이내). 발행 측이 완성된 문장으로 보낸다 —
     *                   이 서비스는 무엇을 알릴지 판단하지 않는다.
     * @param relatedUrl 알림 선택 시 이동 경로.
     */
    record NotificationRaisedEvent(String eventId, UUID memberId, NotifType notifType,
                                   String title, String relatedUrl) {
    }
}
