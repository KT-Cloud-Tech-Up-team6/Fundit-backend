package com.fundit.payment.presentation.dto;

import com.fundit.payment.application.refund.PostShipmentRefundRequestService.PostShipmentRefundRequestResult;
import com.fundit.payment.domain.refund.ExchangeReason;
import com.fundit.payment.domain.refund.ReturnPolicy;

/**
 * 교환 접수 응답. 반품 접수 응답과 대칭으로, FE가 교환비를 직접 계산하지 않도록 사유에 따른
 * 교환 배송비와 추가 결제 금액을 함께 내린다(판매자 귀책·기타는 0원). 실제 수납은 판매자 승인
 * 시점이라 접수 시점에는 금액만 알려준다.
 */
public record ExchangeRequestResponse(Long refundId, String status, long exchangeShippingFee,
                                       long additionalPaymentAmount) {

    public static ExchangeRequestResponse from(PostShipmentRefundRequestResult result, ExchangeReason reason) {
        return new ExchangeRequestResponse(result.refundId(), result.status(), ReturnPolicy.EXCHANGE_SHIPPING_FEE,
                reason.additionalPaymentAmount());
    }
}
