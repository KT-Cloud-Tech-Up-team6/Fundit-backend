package com.fundit.payment.application.reshipment;

import java.util.UUID;

/**
 * 교환 승인 후 fulfillment-service에 재발송을 요청하는 아웃바운드 포트
 * ({@code POST /internal/fundings/{fundingId}/reshipments}). 배송 애그리거트는
 * fulfillment-service 소유라 이 서비스가 직접 쓰지 않는다(DB-per-service).
 *
 * <p>같은 교환 신청으로 두 번 호출돼도 fulfillment 쪽에서 멱등 처리된다
 * ({@code refundRequestId} 기준).
 */
public interface ExchangeReshipmentClient {

    ReshipmentResult request(UUID orderId, Long refundRequestId);

    /** {@code reshipmentCount}는 이 펀딩의 누적 재발송 횟수다(요청이 멱등 무시되면 증가하지 않는다). */
    record ReshipmentResult(String status, int reshipmentCount) {
    }
}
