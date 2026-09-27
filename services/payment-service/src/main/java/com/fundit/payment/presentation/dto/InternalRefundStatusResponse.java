package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.RefundQueryService.FundingRefundStatus;

import java.time.Instant;
import java.util.UUID;

/** 내부 전용 — order-service 펀딩 내역(V03/V06)의 신청 여부 표시용 응답. */
public record InternalRefundStatusResponse(UUID fundingId, Long refundId, String triggerType, String status,
                                            Instant requestedAt) {

    public static InternalRefundStatusResponse from(FundingRefundStatus status) {
        return new InternalRefundStatusResponse(status.fundingId(), status.refundId(), status.triggerType(),
                status.status(), status.requestedAt());
    }
}
