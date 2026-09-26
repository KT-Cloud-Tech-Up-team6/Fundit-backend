package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.OrderSummaryClient;
import com.fundit.payment.application.refund.RefundQueryService.RefundSummary;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.domain.refund.ReturnPolicy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PAYMENT-003 v2 응답 — fundingId가 order-service publicId(UUID)로 채워진다.
 * {@code projectTitle}/{@code lineItems}는 order-service 조회 실패 시 null일 수 있다(V04, 부가 정보).
 *
 * <p>{@code amount}는 화면의 "실 환불 금액"이다 — 반품비가 차감된 건은 결제 원금이 아니라 실제
 * 취소된 금액이 내려온다. {@code returnShippingFee}는 반품 건에만 채워진다(그 외 null).
 */
public record RefundSummaryResponseV2(Long refundId, UUID fundingId, String triggerType, String status, long amount,
                                       Long returnShippingFee, Instant requestedAt, String reasonDetail,
                                       String rejectedReason, Instant completedAt, String projectTitle,
                                       List<RefundLineItemResponse> lineItems) {

    public static RefundSummaryResponseV2 from(RefundSummary summary) {
        OrderSummaryClient.OrderSummary orderSummary = summary.orderSummary();
        return new RefundSummaryResponseV2(summary.refundId(), summary.fundingId(), summary.triggerType(),
                summary.status(), summary.amount(), returnShippingFeeOf(summary.triggerType()),
                summary.requestedAt(), summary.reasonDetail(),
                summary.rejectedReason(), summary.completedAt(),
                orderSummary == null ? null : orderSummary.projectTitle(),
                orderSummary == null ? null : orderSummary.lineItems().stream()
                        .map(RefundLineItemResponse::from).toList());
    }

    /** 반품비는 전 프로젝트 공통 고정액이라 조회 시 계산 없이 상수로 채운다. */
    private static Long returnShippingFeeOf(String triggerType) {
        return RefundTriggerType.RETURN_CHANGE_OF_MIND.name().equals(triggerType)
                ? ReturnPolicy.RETURN_SHIPPING_FEE : null;
    }

    public record RefundLineItemResponse(String rewardName, int quantity, long unitPrice,
                                          List<RefundLineItemOptionResponse> options) {

        public static RefundLineItemResponse from(OrderSummaryClient.LineItem lineItem) {
            return new RefundLineItemResponse(lineItem.rewardName(), lineItem.quantity(), lineItem.unitPrice(),
                    lineItem.options().stream().map(RefundLineItemOptionResponse::from).toList());
        }
    }

    public record RefundLineItemOptionResponse(String optionGroupName, String optionValue) {

        public static RefundLineItemOptionResponse from(OrderSummaryClient.LineItemOption option) {
            return new RefundLineItemOptionResponse(option.optionGroupName(), option.optionValue());
        }
    }
}
