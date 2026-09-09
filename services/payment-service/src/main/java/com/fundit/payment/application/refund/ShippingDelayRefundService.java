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
 * PAYMENT-008 — 발송지연 결제취소 신청. 단순변심/미달자동과 동일하게 UNDER_REVIEW 단계 없이
 * 즉시 처리한다(payment-service CLAUDE.md 구현 노트 — PaymentERD.md 3장 코멘트보다 우선).
 */
@Service
@RequiredArgsConstructor
public class ShippingDelayRefundService {

    private final PaymentRepository paymentRepository;
    private final ShippingStatusClient shippingStatusClient;
    private final RefundExecutionService refundExecutionService;

    @Transactional
    public ShippingDelayRefundResult requestCancel(UUID accountId, Long fundingId) {
        Payment payment = paymentRepository.findCompletedByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (shippingStatusClient.isAlreadyShipped(fundingId)) {
            throw new BusinessException(PaymentErrorCode.ALREADY_SHIPPED);
        }

        RefundExecutionService.RefundExecutionResult result = refundExecutionService
                .executeFullRefundOrAwaitAlternateAccount(fundingId, RefundTriggerType.SHIPPING_DELAY, "발송지연 결제취소");
        return new ShippingDelayRefundResult(result.refundRequestId(), result.status());
    }

    public record ShippingDelayRefundResult(Long refundId, String status) {
    }
}
