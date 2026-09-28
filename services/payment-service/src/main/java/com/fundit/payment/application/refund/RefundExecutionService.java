package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.notification.PaymentNotificationPublisher;
import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundNotificationStatus;
import com.fundit.payment.application.notification.PaymentNotificationPublisher.RefundStatusChangedEvent;
import com.fundit.payment.application.payment.TossApiException;
import com.fundit.payment.application.payment.TossPaymentsClient;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaEntity;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 토스 취소 API 호출 → {@code payment_cancellations} 기록 → {@code refund_requests} 완료 처리 →
 * {@code RefundCompleted} 아웃박스 적재를 한 트랜잭션으로 묶는 공용 실행기. PAYMENT-004/005/007/
 * 008/017이 전부 "토스 전액(또는 부분) 취소 후 환불 기록"이라는 동일한 골격을 공유해 여기로 모았다.
 */
@Service
@RequiredArgsConstructor
public class RefundExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RefundExecutionService.class);

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentCancellationJpaRepository paymentCancellationJpaRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentNotificationPublisher paymentNotificationPublisher;
    private final SettlementHoldService settlementHoldService;

    /**
     * PAYMENT-004/005/017 — 판매자 검토 없이 즉시 전액취소하는 유형. 이벤트 중복 수신 시
     * 이미 CANCELLED면 토스 API를 재호출하지 않고 조용히 무시한다(멱등).
     */
    @Transactional
    public RefundExecutionResult executeFullRefund(UUID fundingId, RefundTriggerType triggerType,
                                                     String cancelReason) {
        Payment payment = paymentRepository.findCompletedOrCancelledByFundingId(fundingId).orElse(null);
        if (payment == null) {
            return failPendingPayment(fundingId, triggerType, cancelReason);
        }

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            log.info("이미 취소 처리된 결제입니다(멱등 무시). fundingId={} paymentId={}", fundingId, payment.getId());
            return RefundExecutionResult.alreadyProcessed();
        }

        return execute(payment, payment.getAmount(), triggerType, cancelReason, null);
    }

    /**
     * 환불할 완료 결제가 없다 — 결제 전에 참여를 취소한 경우다. 대기 중인 결제가 남아 있으면 FAILED로 닫아
     * 결제창에서 뒤늦게 확정되지 않게 한다(돈이 빠지고 주문은 취소로 남는 사고 방지). 대기 결제도 없으면 기존처럼 404.
     *
     * <p>단, 승인 결과 불명(토스 5xx·타임아웃)으로 PENDING에 남은 결제는 토스에선 이미 승인됐을 수 있다. 그래서
     * 닫기 전에 주문번호로 토스를 조회해, 승인돼 있으면 완료로 맞춘 뒤 전액 취소한다. 조회 자체가 실패하면
     * FAILED로 굳히지 않고 예외를 던져 PENDING으로 둔다.
     * ponytail: 이 경우 이벤트는 재시도 없이 끝난다. 구매자가 재확정하면 결제 완료 → order 조정 환불로 이어지지만,
     * 재확정도 없으면 자동 복구는 없다 — 필요해지면 PENDING 결제를 주기적으로 토스와 대조하는 배치를 둘 것.
     */
    private RefundExecutionResult failPendingPayment(UUID fundingId, RefundTriggerType triggerType, String cancelReason) {
        Payment pending = paymentRepository.findPendingByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "완료된 결제를 찾을 수 없습니다."));
        Optional<TossPaymentsClient.TossPaymentLookup> approved = tossPaymentsClient.lookupByOrderId(pending.getPgOrderId())
                .filter(lookup -> lookup.isDone() && lookup.payment().totalAmount() == pending.getAmount());
        if (approved.isPresent()) {
            TossPaymentsClient.TossPaymentResult toss = approved.get().payment();
            // 주문은 이미 취소됐으니 PaymentCompleted·정산 보류 없이 완료로만 맞추고 바로 전액 취소한다.
            pending.markCompleted(toss.paymentKey(), toss.secret(), PaymentMethod.fromTossMethod(toss.method()),
                    toss.easyPayProvider(), toss.approvedAt() == null ? Instant.now() : toss.approvedAt());
            log.warn("결제 전 취소로 보였으나 토스에선 승인된 결제라 전액 취소합니다. fundingId={} paymentId={}",
                    fundingId, pending.getId());
            return execute(pending, pending.getAmount(), triggerType, cancelReason, null);
        }
        pending.markFailed();
        paymentRepository.save(pending);
        log.info("결제 전 참여 취소 — 대기 결제를 실패 처리했습니다. fundingId={} paymentId={}", fundingId, pending.getId());
        return RefundExecutionResult.pendingPaymentClosed();
    }

    /**
     * PAYMENT-005/008 예외 처리 — 원 결제수단 환불이 불가하면(토스 취소 API 실패) 즉시 실패
     * 처리하지 않고 대체 계좌 입력 대기 상태로 전환한다(기능명세서 PAYMENT-005 예외 처리 항목).
     * [범위 밖] 대체 계좌를 실제로 입력받는 API는 이번 구현 범위(PaymentApiSpec.md 9개 API)에
     * 없다 — 별도 엔드포인트 신설이 필요해 보이며, PM/기획 확인이 필요하다.
     */
    @Transactional
    public RefundExecutionResult executeFullRefundOrAwaitAlternateAccount(UUID fundingId, RefundTriggerType triggerType,
                                                                            String cancelReason) {
        Payment payment = paymentRepository.findCompletedOrCancelledByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "완료된 결제를 찾을 수 없습니다."));
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            log.info("이미 취소 처리된 결제입니다(멱등 무시). fundingId={} paymentId={}", fundingId, payment.getId());
            return RefundExecutionResult.alreadyProcessed();
        }
        try {
            return execute(payment, payment.getAmount(), triggerType, cancelReason, null);
        } catch (BusinessException e) {
            if (e.getErrorCode() != PaymentErrorCode.PG_CANCEL_FAILED) {
                throw e;
            }
            log.warn("원 결제수단 환불 실패 — 대체 계좌 입력 대기 상태로 전환합니다. fundingId={}", fundingId, e);
            refundRequestRepository.save(RefundRequest.awaitingAlternateAccount(triggerType, fundingId, payment.getId(), cancelReason));
            paymentNotificationPublisher.publishRefundStatusChanged(new RefundStatusChangedEvent(
                    fundingId, payment.getMemberId(), RefundNotificationStatus.AWAITING_ALTERNATE_ACCOUNT));
            return RefundExecutionResult.awaitingAlternateAccount();
        }
    }

    /** PAYMENT-007 — 판매자 승인에 의한 취소(전액 또는 반품비 차감 부분취소). */
    @Transactional
    public RefundExecutionResult executeApprovedRefund(RefundRequest refundRequest, long cancelAmount,
                                                         String cancelReason) {
        Payment payment = paymentRepository.findById(refundRequest.getPaymentId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return execute(payment, cancelAmount, refundRequest.getTriggerType(), cancelReason, refundRequest);
    }

    private RefundExecutionResult execute(Payment payment, long cancelAmount, RefundTriggerType triggerType,
                                           String cancelReason, RefundRequest existingRequest) {
        TossPaymentsClient.TossCancelResult cancelResult;
        try {
            cancelResult = tossPaymentsClient.cancel(payment.getPgPaymentKey(), cancelAmount, cancelReason);
        } catch (TossApiException e) {
            throw new BusinessException(PaymentErrorCode.PG_CANCEL_FAILED, e.getTossMessage());
        }

        boolean isFullRefund = cancelAmount >= payment.getAmount();
        if (isFullRefund) {
            payment.markCancelled();
            paymentRepository.save(payment);
            // 전액 환불 시에만 에스크로 보류를 해제한다 — 부분취소(반품비 차감)는 나머지 금액이
            // 여전히 정산 대상이므로 HOLDING을 유지한다.
            settlementHoldService.releaseToRefund(payment.getId());
        }

        paymentCancellationJpaRepository.save(PaymentCancellationJpaEntity.builder()
                .paymentId(payment.getId())
                .refundRequestId(existingRequest == null ? null : existingRequest.getId())
                .pgTransactionKey(cancelResult.transactionKey())
                .cancelAmount(cancelAmount)
                .cancelReason(cancelReason)
                .canceledAt(cancelResult.canceledAt() == null ? Instant.now() : cancelResult.canceledAt())
                .build());

        RefundRequest completed = existingRequest == null
                ? RefundRequest.completeImmediately(triggerType, payment.getFundingId(), payment.getId(), isFullRefund,
                        cancelReason)
                : approveExisting(existingRequest, isFullRefund);
        RefundRequest saved = refundRequestRepository.save(completed);

        paymentEventPublisher.publishRefundCompleted(new PaymentEventPublisher.RefundCompletedEvent(
                payment.getId(), payment.getFundingId(), payment.getCouponIssuanceIds(),
                triggerType.toOrderServiceReason(), isFullRefund));
        paymentNotificationPublisher.publishRefundStatusChanged(new RefundStatusChangedEvent(
                payment.getFundingId(), payment.getMemberId(), RefundNotificationStatus.COMPLETED));

        return new RefundExecutionResult(saved.getId(), saved.getStatus().name(), isFullRefund);
    }

    private RefundRequest approveExisting(RefundRequest refundRequest, boolean isFullRefund) {
        refundRequest.approve(isFullRefund);
        return refundRequest;
    }

    public record RefundExecutionResult(Long refundRequestId, String status, boolean fullRefund) {
        static RefundExecutionResult alreadyProcessed() {
            return new RefundExecutionResult(null, "ALREADY_PROCESSED", true);
        }

        static RefundExecutionResult awaitingAlternateAccount() {
            return new RefundExecutionResult(null, "AWAITING_ALTERNATE_ACCOUNT", true);
        }

        static RefundExecutionResult pendingPaymentClosed() {
            return new RefundExecutionResult(null, "PENDING_PAYMENT_CLOSED", false);
        }
    }
}
