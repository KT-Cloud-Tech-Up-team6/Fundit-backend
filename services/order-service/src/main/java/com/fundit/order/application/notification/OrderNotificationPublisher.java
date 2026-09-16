package com.fundit.order.application.notification;

import java.util.UUID;

/**
 * order-service가 발행하는 알림 이벤트({@code notification.raised.v1})의 아웃바운드 포트.
 * 호출부는 같은 트랜잭션에서 아웃박스에만 적재한다({@code OutboxOrderNotificationPublisher}).
 * 실제 채널 발행은 워커가 재시도하고, {@code OrderNotificationTransport} 구현체가 완성된
 * 문구로 조립해 보낸다(fulfillment-service {@code FulfillmentNotificationPublisher}와 동일 패턴).
 */
public interface OrderNotificationPublisher {

    /** ORDER-016 부수 효과 — 재입고 알림 신청자에게 리워드 재입고를 안내. */
    void publishRewardRestocked(RewardRestockedEvent event);

    /** 쿠폰 만료임박 리마인더 — 보유자에게 안내. */
    void publishCouponExpiring(CouponExpiringEvent event);

    record RewardRestockedEvent(Long rewardId, UUID memberId) {
    }

    record CouponExpiringEvent(Long couponIssuanceId, UUID memberId) {
    }
}
