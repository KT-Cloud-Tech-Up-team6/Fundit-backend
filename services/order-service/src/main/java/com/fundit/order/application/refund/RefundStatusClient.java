package com.fundit.order.application.refund;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 펀딩 내역(ORDER-004/005)의 "신청 여부" 표시용 — payment-service 내부 API
 * ({@code GET /internal/refunds/statuses})를 조회한다. 신청 후 카드 버튼이 "취소 내역"·
 * "반품·교환 내역"으로 바뀌어야 하는데, 이 서비스는 환불 <b>완료</b> 이벤트만 구독하므로
 * 진행 중(REQUESTED/UNDER_REVIEW/APPROVED/PROCESSING) 신청을 알 방법이 없다.
 */
public interface RefundStatusClient {

    /**
     * 주문별 신청 이력(최신순). 조회 실패 시 빈 Map을 돌려준다 — 신청 여부는 부가 정보라
     * 목록 자체는 내려가야 한다(fulfillment 배치 조회와 동일한 degrade 정책).
     */
    Map<UUID, List<RefundStatus>> fetchBatch(List<UUID> orderIds);

    /** {@code triggerType}/{@code status}는 payment-service의 enum 이름을 그대로 실어 온다. */
    record RefundStatus(Long refundId, String triggerType, String status, Instant requestedAt) {
    }
}
