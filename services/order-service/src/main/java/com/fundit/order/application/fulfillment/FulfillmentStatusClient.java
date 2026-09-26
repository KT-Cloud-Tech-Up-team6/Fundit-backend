package com.fundit.order.application.fulfillment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ORDER-005 {@code availableActions()} 판단용 — fulfillment-service 내부 API(FULFILLMENT-008,
 * {@code GET /internal/fundings/{fundingId}/fulfillment-status})를 조회한다. payment-service
 * {@code ShippingStatusClient}가 이미 호출 중인 것과 동일한 엔드포인트·계약이다.
 */
public interface FulfillmentStatusClient {

    FulfillmentStatus fetch(UUID orderId);

    /** ORDER-004 목록(V03/V06)용 배치 조회 — 건별 호출(N+1)을 피한다. 조회 실패한 건은 결과에서 빠진다. */
    Map<UUID, FulfillmentStatus> fetchBatch(List<UUID> orderIds);

    /**
     * {@code deliveredAt}은 배송 완료 시각이며 완료 전이면 null이다 — 반품·교환 신청 기한
     * ("수령 후 7일", 환불 정책 V.1.0)을 판정하는 기준일이라 boolean으로 줄이지 않는다.
     */
    record FulfillmentStatus(boolean isAlreadyShipped, Instant deliveredAt) {

        public boolean isDelivered() {
            return deliveredAt != null;
        }
    }
}
