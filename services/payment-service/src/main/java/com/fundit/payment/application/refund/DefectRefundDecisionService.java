package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.application.notification.PaymentNotificationPublisher;
import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundNotificationStatus;
import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundStatusChangedEvent;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.domain.refund.ReturnPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PAYMENT-007 — 발송 후 환불 신청의 판매자 검토/승인/반려. 판매자 본인 소유 건인지 검증한다
 * (security.md S4). 대상은 하자환불(DEFECT)·구매자 귀책 반품(RETURN_CHANGE_OF_MIND)·교환(EXCHANGE)
 * 3종이며, 판매자 화면이 신청 유형별로 다른 API를 쓰지 않도록 한 경로로 받는다.
 *
 * <p>승인 이후가 유형별로 갈린다 — 하자환불·반품은 결제취소(전액/반품비 차감 부분취소)로 끝나고,
 * 교환은 취소 없이 교환비 수납·재발송으로 이어져 {@link ExchangeService}가 이어받는다. 반려는
 * 세 유형이 동일하다(상태 전이 + 알림, 이벤트 미발행).
 */
@Service
@RequiredArgsConstructor
public class DefectRefundDecisionService {

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final OrderFundingClient orderFundingClient;
    private final RefundExecutionService refundExecutionService;
    private final ExchangeService exchangeService;
    private final PaymentNotificationPublisher paymentNotificationPublisher;

    @Transactional
    public DefectDecisionResult decide(UUID accountId, Long refundId, boolean approve, String reason) {
        RefundRequest refundRequest = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!refundRequest.getTriggerType().isSellerDecisionTarget()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "판매자 검토 대상 신청이 아닙니다.");
        }

        OrderFundingClient.FundingSnapshot snapshot = orderFundingClient.fetch(refundRequest.getFundingId());
        if (snapshot.sellerId() == null || !snapshot.sellerId().equals(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        Payment payment = paymentRepository.findById(refundRequest.getPaymentId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (!approve) {
            RefundRequest saved = refundRequestRepository.save(rejectedCopy(refundRequest, reason));
            paymentNotificationPublisher.publishRefundStatusChanged(new RefundStatusChangedEvent(
                    payment.getFundingId(), payment.getMemberId(), RefundNotificationStatus.REJECTED));
            return new DefectDecisionResult(saved.getId(), saved.getStatus().name());
        }

        if (refundRequest.getTriggerType() == RefundTriggerType.EXCHANGE) {
            ExchangeService.ExchangeApprovalResult exchange = exchangeService.approve(refundRequest);
            return new DefectDecisionResult(exchange.refundId(), exchange.status());
        }

        // 환불 정책 V.1.0 — 구매자 귀책 반품은 반품 배송비를 뺀 금액만 환불한다(부분취소).
        // 판매자 귀책(하자·파손·오배송 등)은 판매자 부담이라 전액 취소다.
        boolean buyerFaultReturn = refundRequest.getTriggerType() == RefundTriggerType.RETURN_CHANGE_OF_MIND;
        long cancelAmount = buyerFaultReturn
                ? ReturnPolicy.refundAmountAfterReturnFee(payment.getAmount()) : payment.getAmount();
        RefundExecutionService.RefundExecutionResult result = refundExecutionService.executeApprovedRefund(
                refundRequest, cancelAmount, buyerFaultReturn ? "반품 승인(반품비 차감)" : "하자환불 승인");
        return new DefectDecisionResult(result.refundRequestId(), result.status());
    }

    private RefundRequest rejectedCopy(RefundRequest refundRequest, String reason) {
        refundRequest.reject(reason);
        return refundRequest;
    }

    public record DefectDecisionResult(Long refundId, String status) {
    }
}
