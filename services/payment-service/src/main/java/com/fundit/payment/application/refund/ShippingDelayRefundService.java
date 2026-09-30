package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    /**
     * 트랜잭션을 걸지 않는다 — 여기서는 조회·검증만 하고, 토스 취소는 {@link RefundExecutionService}가 트랜잭션
     * 밖에서 부른다(바깥에 트랜잭션이 있으면 토스 호출이 다시 그 안으로 들어간다).
     */
    public ShippingDelayRefundResult requestCancel(UUID accountId, UUID fundingId) {
        Payment payment = paymentRepository.findCompletedByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        ShippingStatusClient.ShippingStatus status = shippingStatusClient.fetch(fundingId);
        if (status.isAlreadyShipped()) {
            throw new BusinessException(PaymentErrorCode.ALREADY_SHIPPED);
        }
        if (!status.isDelayed()) {
            throw new BusinessException(PaymentErrorCode.NOT_YET_DELAYED);
        }

        RefundExecutionService.RefundExecutionResult result = refundExecutionService
                .executeFullRefundOrAwaitAlternateAccount(fundingId, RefundTriggerType.SHIPPING_DELAY, "발송지연 결제취소");
        return new ShippingDelayRefundResult(result.refundRequestId(), result.status());
    }

    public record ShippingDelayRefundResult(Long refundId, String status) {
    }
}
