package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** PAYMENT-007 — 하자환불 검토/승인/반려. 판매자 본인 소유 건인지 검증한다(security.md S4). */
@Service
@RequiredArgsConstructor
public class DefectRefundDecisionService {

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final OrderFundingClient orderFundingClient;
    private final RefundExecutionService refundExecutionService;

    @Transactional
    public DefectDecisionResult decide(UUID accountId, Long refundId, boolean approve, String reason) {
        RefundRequest refundRequest = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (refundRequest.getTriggerType() != RefundTriggerType.DEFECT) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "하자환불 신청이 아닙니다.");
        }

        OrderFundingClient.FundingSnapshot snapshot = orderFundingClient.fetch(refundRequest.getFundingId());
        if (snapshot.sellerId() == null || !snapshot.sellerId().equals(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        if (!approve) {
            RefundRequest saved = refundRequestRepository.save(rejectedCopy(refundRequest, reason));
            return new DefectDecisionResult(saved.getId(), saved.getStatus().name());
        }

        Payment payment = paymentRepository.findById(refundRequest.getPaymentId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        // [정책 확인 필요] 반품비 차감 등 부분취소 금액 산정 방식이 미확정이다(PaymentERD.md 6장
        // "반품비 처리 방식") — 확정 전까지는 전액취소로 처리한다.
        RefundExecutionService.RefundExecutionResult result = refundExecutionService.executeApprovedRefund(
                refundRequest, payment.getAmount(), "하자환불 승인");
        return new DefectDecisionResult(result.refundRequestId(), result.status());
    }

    private RefundRequest rejectedCopy(RefundRequest refundRequest, String reason) {
        refundRequest.reject(reason);
        return refundRequest;
    }

    public record DefectDecisionResult(Long refundId, String status) {
    }
}
