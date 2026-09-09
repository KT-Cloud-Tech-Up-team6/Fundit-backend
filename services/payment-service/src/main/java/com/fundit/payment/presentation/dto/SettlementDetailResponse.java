package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.settlement.SettlementQueryService.SettlementDetail;

import java.util.List;

/** PAYMENT-009 응답. */
public record SettlementDetailResponse(Long settlementBatchId, String batchType, String status, long grossAmount,
                                        long platformFeeAmount, long refundDeductionAmount,
                                        long couponDeductionAmount, long totalAmount,
                                        List<SettlementLineItemResponse> lineItems) {

    public static SettlementDetailResponse from(SettlementDetail detail) {
        var batch = detail.batch();
        return new SettlementDetailResponse(batch.getId(), batch.getBatchType().name(), batch.getStatus().name(),
                batch.getGrossAmount(), batch.getPlatformFeeAmount(), batch.getRefundDeductionAmount(),
                batch.getCouponDeductionAmount(), batch.getTotalAmount(),
                detail.lineItems().stream().map(SettlementLineItemResponse::from).toList());
    }
}
