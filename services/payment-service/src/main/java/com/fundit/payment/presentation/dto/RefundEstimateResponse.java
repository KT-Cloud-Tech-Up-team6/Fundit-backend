package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.RefundEstimateService.RefundEstimate;

import java.util.UUID;

/**
 * R05 — 환불 신청 전 사전 계산 응답. {@code returnShippingFee}는 구매자 귀책 반품일 때만 0보다
 * 크고, {@code refundAmount}는 귀책이 불분명한 건(사유 "기타")에서 null이다 — 화면은 이때
 * 확정액을 표시하지 않고 "검토 후 확정" 문구를 보여준다(환불 정책 V.1.0).
 */
public record RefundEstimateResponse(UUID orderId, long rewardAmount, long shippingFee, long discountAmount,
                                      long returnShippingFee, Long refundAmount) {

    public static RefundEstimateResponse from(RefundEstimate estimate) {
        return new RefundEstimateResponse(estimate.orderId(), estimate.rewardAmount(), estimate.shippingFee(),
                estimate.discountAmount(), estimate.returnShippingFee(), estimate.refundAmount());
    }
}
