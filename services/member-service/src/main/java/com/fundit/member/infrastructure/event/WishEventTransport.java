package com.fundit.member.infrastructure.event;

import java.util.UUID;

/**
 * 아웃박스에 적재된 찜 이벤트를 실제 채널로 보내는 전송 포트.
 * Kafka 배선(#50)이 develop에 들어오면 이 인터페이스의 구현체만 교체한다
 * (project/order/payment/fulfillment의 {@code *EventTransport}와 같은 자리).
 */
public interface WishEventTransport {

    /** {@code project.wished.v1} */
    void sendWished(WishEvent event);

    /** {@code project.unwished.v1} */
    void sendUnwished(WishEvent event);

    /**
     * 두 이벤트의 payload가 같아 레코드를 하나만 둔다 — 토픽은 호출하는 메서드가 정한다.
     *
     * <p>{@code eventId}는 {@code "member:{outboxId}"}다. 아웃박스 id가 BIGINT IDENTITY라
     * 워커가 재발행해도 값이 변하지 않아 소비 측 멱등의 근거가 된다(event-convention.md 5번).
     */
    record WishEvent(String eventId, UUID memberId, Long projectId) {
    }
}
