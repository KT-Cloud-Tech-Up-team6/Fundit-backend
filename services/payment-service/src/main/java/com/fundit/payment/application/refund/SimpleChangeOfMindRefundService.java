package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 성립(GOAL_ACHIEVED) 이후 발송 전 단순변심 환불 신청 — 모금 진행 중 참여 취소
 * (order-service ORDER-014, {@code POST /api/v1/orders/{orderId}/cancel})와는 별개 흐름이다.
 * 발송지연 취소(PAYMENT-008)와 동일하게 판매자 검토 없이 즉시 전액환불 처리하되, "지연" 여부는
 * 따지지 않고 아직 발송 전이기만 하면 허용한다.
 */
@Service
@RequiredArgsConstructor
public class SimpleChangeOfMindRefundService {

    private final PaymentRepository paymentRepository;
    private final ShippingStatusClient shippingStatusClient;
    private final RefundExecutionService refundExecutionService;

    @Transactional
    public SimpleChangeOfMindRefundResult requestCancel(UUID accountId, UUID fundingId) {
        Payment payment = paymentRepository.findCompletedByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (shippingStatusClient.fetch(fundingId).isAlreadyShipped()) {
            throw new BusinessException(PaymentErrorCode.ALREADY_SHIPPED);
        }

        RefundExecutionService.RefundExecutionResult result = refundExecutionService
                .executeFullRefundOrAwaitAlternateAccount(fundingId, RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "단순변심 환불신청");
        return new SimpleChangeOfMindRefundResult(result.refundRequestId(), result.status());
    }

    public record SimpleChangeOfMindRefundResult(Long refundId, String status) {
    }
}
