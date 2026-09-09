package com.fundit.payment.application.settlement;

import java.time.Instant;
import java.util.UUID;

/**
 * PAYMENT-014 — 배송완료 통지 구독 포트. shipping-service가 아직 로드맵상의 예정 서비스라
 * 실제 발행 주체가 없다 — 비즈니스 로직({@link SettlementScheduleService})은 완성해두고
 * 실제 트리거 배선만 shipping-service 개발 시점으로 미룬다.
 */
public interface ShippingCompletionListener {

    void onShippingCompleted(ShippingCompletedEvent event);

    record ShippingCompletedEvent(Long fundingId, Long projectId, UUID sellerId, Instant completedAt) {
    }
}
