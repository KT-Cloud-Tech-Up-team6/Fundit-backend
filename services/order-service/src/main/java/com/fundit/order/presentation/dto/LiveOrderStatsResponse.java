package com.fundit.order.presentation.dto;

import com.fundit.order.infrastructure.persistence.funding.query.LiveOrderStatsProjection;

import java.util.UUID;

/**
 * 방송 중 주문 지표. 금액은 쿠폰 할인 반영 전 리워드 합산액(배송비 제외)이다.
 * {@code pending}은 아직 결제 전(PENDING) 주문 — 방송 중에는 이쪽이 대부분이다.
 */
public record LiveOrderStatsResponse(UUID liveId, long paidCount, long paidAmount,
                                       long pendingCount, long pendingAmount) {

    public static LiveOrderStatsResponse from(UUID liveId, LiveOrderStatsProjection stats) {
        return new LiveOrderStatsResponse(liveId, stats.getPaidCount(), stats.getPaidAmount(),
                stats.getPendingCount(), stats.getPendingAmount());
    }
}
