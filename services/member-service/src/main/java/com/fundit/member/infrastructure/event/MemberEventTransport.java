package com.fundit.member.infrastructure.event;

import java.util.UUID;

/**
 * 아웃박스에 적재된 이벤트를 실제 채널로 보내는 전송 포트.
 *
 * <p>{@code eventId}는 {@code "member:{outboxId}"}다. 아웃박스 id가 BIGINT IDENTITY라
 * 워커가 재발행해도 값이 변하지 않아 소비 측 멱등의 근거가 된다(event-convention.md 5번).
 */
public interface MemberEventTransport {

    /** {@code project.wished.v1} */
    void sendWished(WishEvent event);

    /** {@code project.unwished.v1} */
    void sendUnwished(WishEvent event);

    /** {@code member.signed-up.v1} */
    void sendSignedUp(SignedUpEvent event);

    /** 찜 등록·해제의 payload가 같아 레코드를 하나만 둔다 — 토픽은 호출하는 메서드가 정한다. */
    record WishEvent(String eventId, UUID memberId, Long projectId) {
    }

    /**
     * 가입 이벤트에는 {@code projectId}가 없어 레코드를 따로 둔다 — 하나로 합치면 JSON에
     * {@code "projectId":null}이 실려 나가 계약에 없는 필드가 생긴다.
     */
    record SignedUpEvent(String eventId, UUID memberId) {
    }
}
