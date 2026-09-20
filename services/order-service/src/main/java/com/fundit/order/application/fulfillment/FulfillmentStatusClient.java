package com.fundit.order.application.fulfillment;

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

    record FulfillmentStatus(boolean isAlreadyShipped, boolean isDelivered) {
    }
}
