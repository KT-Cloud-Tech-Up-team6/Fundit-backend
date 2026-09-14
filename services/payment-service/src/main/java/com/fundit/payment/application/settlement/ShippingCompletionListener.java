package com.fundit.payment.application.settlement;

import java.time.Instant;
import java.util.UUID;

/**
 * PAYMENT-014 — 배송완료 통지 구독 포트. fulfillment-service가 아직 이 이벤트를 발행하지
 * 않아(FULFILLMENT-007 목업 배치가 배송완료 전이를 만들 뿐 이벤트 발행은 없음) 실제 발행
 * 주체가 없다 — 비즈니스 로직({@link SettlementScheduleService})은 완성해두고 실제 트리거
 * 배선만 fulfillment-service 쪽 이벤트 발행 연동 시점으로 미룬다.
 */
public interface ShippingCompletionListener {

    void onShippingCompleted(ShippingCompletedEvent event);

    record ShippingCompletedEvent(Long fundingId, Long projectId, UUID sellerId, Instant completedAt) {
    }
}
