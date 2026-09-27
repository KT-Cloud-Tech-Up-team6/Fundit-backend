package com.fundit.order.presentation.dto;

import com.fundit.order.application.refund.RefundStatusClient;

import java.time.Instant;

/**
 * 펀딩 내역 카드의 버튼 전환("취소 내역"·"반품·교환 내역")용 — payment-service가 들고 있는
 * 신청 이력을 그대로 내려준다. 값은 payment-service의 enum 이름(문자열)이다.
 */
public record RefundRequestStatusResponse(Long refundId, String triggerType, String status, Instant requestedAt) {

    public static RefundRequestStatusResponse from(RefundStatusClient.RefundStatus status) {
        return new RefundRequestStatusResponse(status.refundId(), status.triggerType(), status.status(),
                status.requestedAt());
    }
}
