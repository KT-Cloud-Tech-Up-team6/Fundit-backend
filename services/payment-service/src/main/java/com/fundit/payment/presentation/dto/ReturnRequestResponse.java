package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.PostShipmentRefundRequestService.PostShipmentRefundRequestResult;

/**
 * 반품 접수 응답. FE가 반품비를 직접 계산하지 않도록 결제액·반품비·예상 환불액을 함께 내린다
 * (실제 환불은 판매자가 회수를 확인하고 승인한 시점에 실행된다).
 */
public record ReturnRequestResponse(Long refundId, String status, long paymentAmount, long returnShippingFee,
                                     long estimatedRefundAmount) {

    public static ReturnRequestResponse from(PostShipmentRefundRequestResult result) {
        return new ReturnRequestResponse(result.refundId(), result.status(), result.paymentAmount(),
                result.returnShippingFee(), result.estimatedRefundAmount());
    }
}
