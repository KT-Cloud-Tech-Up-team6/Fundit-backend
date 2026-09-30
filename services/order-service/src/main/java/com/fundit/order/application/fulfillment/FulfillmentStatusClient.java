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

    /**
     * ORDER-004 목록(V03/V06)용 배치 조회 — 건별 호출(N+1)을 피한다. 조회 실패한 건은 결과에서 빠진다.
     *
     * <p>{@code isDelayed}/{@code hasProgressRecord}는 여기서 채워지지 않는다(항상 false) —
     * 둘 다 프로젝트 단위 판정이라 {@link #fetchProjectStatuses(List)}로 따로 조회해 합친다.
     */
    Map<UUID, FulfillmentStatus> fetchBatch(List<UUID> orderIds);

    /**
     * 프로젝트 단위 판정값(발송지연 여부·진행 기록 유무) 배치 조회 — 목록의 진행 단계 배지·가능
     * 액션용. 조회 실패하거나 응답에 없는 프로젝트는 결과에서 빠지며, 호출부는 {@link
     * ProjectFulfillment#NONE}("지연 아님·기록 없음")으로 본다 — 목록 자체는 내려가야 하고,
     * 누를 수 없는 취소 버튼이나 근거 없는 "제작 중" 배지를 보여주는 것보다 낫다.
     */
    Map<UUID, ProjectFulfillment> fetchProjectStatuses(List<UUID> projectIds);

    /**
     * {@code deliveredAt}은 배송 완료 시각이며 완료 전이면 null이다 — 반품·교환 신청 기한
     * ("수령 후 7일", 환불 정책 V.1.0)을 판정하는 기준일이라 boolean으로 줄이지 않는다.
     */
    record FulfillmentStatus(boolean isAlreadyShipped, boolean isDelayed, Instant deliveredAt,
                              boolean hasProgressRecord) {

        public boolean isDelivered() {
            return deliveredAt != null;
        }
    }

    /**
     * 프로젝트 단위 판정값. {@code hasProgressRecord}는 판매자가 제작·배송 현황에 진행 기록을 올린
     * 적이 있는지로, 진행 단계 배지의 {@code IN_PRODUCTION}("제작 중") 판정에 쓴다.
     */
    record ProjectFulfillment(boolean isDelayed, boolean hasProgressRecord) {

        /** 조회 실패·응답 누락 시의 보수적 기본값. */
        public static final ProjectFulfillment NONE = new ProjectFulfillment(false, false);
    }
}
