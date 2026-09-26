package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.RefundEstimateService.RefundEstimate;

import java.util.UUID;

/**
 * R05 — 환불·교환 신청 전 사전 계산 응답. 화면이 "결제 금액 23,000원 · 반품 배송비 −5,000원 →
 * 예상 환불액 18,000원"을 그대로 그릴 수 있도록 결제 금액과 차감·가산 항목을 각각 내려보낸다.
 *
 * <p>{@code returnShippingFee}는 구매자 귀책 반품일 때만, {@code additionalPaymentAmount}는
 * 구매자 귀책 교환일 때만 0보다 크다. {@code refundAmount}는 교환(환불 없음)과 귀책이 불분명한
 * 건에서 null이고, {@code confirmed=false}면 화면은 확정액 대신 "확인 시"/"접수 후 안내"로
 * 표기한다(환불 정책 V.1.0).
 */
public record RefundEstimateResponse(UUID orderId, long paymentAmount, long rewardAmount, long shippingFee,
                                      long discountAmount, long returnShippingFee, long additionalPaymentAmount,
                                      Long refundAmount, boolean confirmed) {

    public static RefundEstimateResponse from(RefundEstimate estimate) {
        return new RefundEstimateResponse(estimate.orderId(), estimate.paymentAmount(), estimate.rewardAmount(),
                estimate.shippingFee(), estimate.discountAmount(), estimate.returnShippingFee(),
                estimate.additionalPaymentAmount(), estimate.refundAmount(), estimate.confirmed());
    }
}
