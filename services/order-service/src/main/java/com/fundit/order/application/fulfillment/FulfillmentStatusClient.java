package com.fundit.order.application.fulfillment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ORDER-005 {@code availableActions()} 판단용 — fulfillment-service 내부 API(FULFILLMENT-008,
 * {@code GET /internal/fundings/{fundingId}/fulfillment-status})를 조회한다. payment-service
 * {@code ShippingStatusClient}가 이미 호출 중인 것과 동일한 엔드포인트·계약이다.
 */
public interface FulfillmentStatusClient {

    FulfillmentStatus fetch(UUID orderId);

    /**
     * ORDER-004 목록(V03/V06)용 배치 조회 — 건별 호출(N+1)을 피한다. 조회 실패한 건은 결과에서 빠진다.
     *
     * <p>{@code isDelayed}는 여기서 채워지지 않는다(항상 false) — 지연은 프로젝트 단위 판정이라
     * {@link #fetchDelayedProjectIds(List)}로 따로 조회해 합친다.
     */
    Map<UUID, FulfillmentStatus> fetchBatch(List<UUID> orderIds);

    /**
     * 발송 예정일이 지난 프로젝트 id 집합(목록의 발송지연 배지·가능 액션용). 조회 실패 시 빈 집합을
     * 돌려준다 — 목록 자체는 내려가야 하고, "지연 아님"으로 보는 쪽이 누를 수 없는 취소 버튼을
     * 보여주는 것보다 낫다.
     */
    Set<UUID> fetchDelayedProjectIds(List<UUID> projectIds);

    /**
     * {@code deliveredAt}은 배송 완료 시각이며 완료 전이면 null이다 — 반품·교환 신청 기한
     * ("수령 후 7일", 환불 정책 V.1.0)을 판정하는 기준일이라 boolean으로 줄이지 않는다.
     */
    record FulfillmentStatus(boolean isAlreadyShipped, boolean isDelayed, Instant deliveredAt) {

        public boolean isDelivered() {
            return deliveredAt != null;
        }
    }
}
