package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.RefundEstimateService.RefundEstimate;

import java.util.UUID;

/** R05 — 환불 신청 전 사전 계산 응답. */
public record RefundEstimateResponse(UUID orderId, long rewardAmount, long shippingFee, long discountAmount,
                                      long refundAmount) {

    public static RefundEstimateResponse from(RefundEstimate estimate) {
        return new RefundEstimateResponse(estimate.orderId(), estimate.rewardAmount(), estimate.shippingFee(),
                estimate.discountAmount(), estimate.refundAmount());
    }
}
