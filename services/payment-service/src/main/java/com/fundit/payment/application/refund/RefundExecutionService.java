package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * PAYMENT-004/005/007/008/017이 공유하는 환불 실행기 — 전부 "토스 전액(또는 부분) 취소 후 환불 기록"이라는
 * 같은 골격이다.
 *
 * <p>토스 취소 호출은 DB 트랜잭션 밖에서 한다(#182). 한 트랜잭션으로 묶으면 토스에선 취소됐는데 로컬 커밋이
 * 실패했을 때 결제·환불 내역·{@code RefundCompleted}·정산 보류 해제가 전부 롤백돼, 이미 환불된 금액이 판매자
 * 정산에 들어갈 수 있다. 그래서 세 단계로 나눈다.
 * <ol>
 *   <li>취소 요청 기록(트랜잭션 1) — {@code refund_requests}를 "취소 요청됨"(PROCESSING + 취소 금액)으로 커밋</li>
 *   <li>토스 취소 호출(트랜잭션 밖)</li>
 *   <li>확정(트랜잭션 2) — 결제 취소, 취소 내역, 환불 완료, 정산 보류 해제, {@code RefundCompleted} 아웃박스</li>
 * </ol>
 * 2~3 사이에서 멈춘 건은 {@link #reconcileCancelsRequestedBefore}(대사 배치)가 토스 조회로 맞춘다.
 */
@Service
public class RefundExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RefundExecutionService.class);

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentCancellationJpaRepository paymentCancellationJpaRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentNotificationPublisher paymentNotificationPublisher;
    private final SettlementHoldService settlementHoldService;
    private final TransactionTemplate transactionTemplate;
    /** 이 시간 안에 요청된 취소는 다른 호출이 아직 토스를 기다리는 중으로 본다 — 대사 배치와 같은 기준. */
    private final Duration staleAfter;

    public RefundExecutionService(PaymentRepository paymentRepository, TossPaymentsClient tossPaymentsClient,
                                  PaymentCancellationJpaRepository paymentCancellationJpaRepository,
                                  RefundRequestRepository refundRequestRepository,
                                  PaymentEventPublisher paymentEventPublisher,
                                  PaymentNotificationPublisher paymentNotificationPublisher,
                                  SettlementHoldService settlementHoldService, TransactionTemplate transactionTemplate,
                                  @Value("${refund-cancel-reconcile.stale-after-minutes:5}") long staleAfterMinutes) {
        this.paymentRepository = paymentRepository;
        this.tossPaymentsClient = tossPaymentsClient;
        this.paymentCancellationJpaRepository = paymentCancellationJpaRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.paymentNotificationPublisher = paymentNotificationPublisher;
        this.settlementHoldService = settlementHoldService;
        this.transactionTemplate = transactionTemplate;
        this.staleAfter = Duration.ofMinutes(staleAfterMinutes);
    }

    /**
     * PAYMENT-004/017 — 판매자 검토 없이 즉시 전액취소하는 유형. 이벤트 중복 수신 시 이미 CANCELLED면 토스 API를
     * 재호출하지 않고 조용히 무시한다(멱등). 취소 요청이 이미 진행 중이면 그 요청을 이어받는다.
     */
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

        return cancel(payment, requestFullCancel(payment, triggerType, cancelReason));
    }

    /**
     * 환불할 완료 결제가 없다 — 결제 전에 참여를 취소한 경우다. 대기 중인 결제가 남아 있으면 FAILED로 닫아
     * 결제창에서 뒤늦게 확정되지 않게 한다(돈이 빠지고 주문은 취소로 남는 사고 방지). 대기 결제도 없으면 기존처럼 404.
     *
     * <p>단, 승인 결과 불명(토스 5xx·타임아웃)으로 PENDING에 남은 결제는 토스에선 이미 승인됐을 수 있다. 그래서
     * 닫기 전에 주문번호로 토스를 조회해, 승인돼 있으면 완료로 맞춘 뒤 전액 취소한다. FAILED로 닫는 건 토스에 결제가
     * 없거나 앞으로도 승인될 수 없는 상태일 때뿐이고, 그마저 조건부 UPDATE라 동시에 끝난 승인을 덮지 않는다.
     * 판단할 수 없으면(조회 실패, 승인 진행 중) 취소 요청만 남겨 두고 대사 배치가 이어서 대조한다.
     */
    private RefundExecutionResult failPendingPayment(UUID fundingId, RefundTriggerType triggerType, String cancelReason) {
        Payment pending = paymentRepository.findPendingByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "완료된 결제를 찾을 수 없습니다."));
        Optional<TossPaymentsClient.TossPaymentLookup> lookup;
        try {
            lookup = tossPaymentsClient.lookupByOrderId(pending.getPgOrderId());
        } catch (DependencyFailureException e) {
            log.warn("대기 결제의 토스 조회 실패 — 취소 요청만 남기고 대사 배치로 넘깁니다. fundingId={} paymentId={}",
                    fundingId, pending.getId(), e);
            requestFullCancel(pending, triggerType, cancelReason);
            return RefundExecutionResult.pendingPaymentUnresolved();
        }
        if (isApproved(lookup, pending)) {
            log.warn("결제 전 취소로 보였으나 토스에선 승인된 결제라 전액 취소합니다. fundingId={} paymentId={}",
                    fundingId, pending.getId());
            markCompletedFromToss(pending, lookup.get().payment());
            RequestedCancel requested = transactionTemplate.execute(status -> {
                paymentRepository.save(pending);
                return openCancelRequest(pending, triggerType, cancelReason);
            });
            return cancel(pending, requested);
        }
        if (lookup.isPresent() && !lookup.get().isNeverApprovable()) {
            // 승인 진행 중(IN_PROGRESS)·금액 불일치 등 — FAILED로 굳히지 않는다. 대사 배치가 다시 대조한다.
            log.warn("대기 결제를 닫지 않고 대사 배치로 넘깁니다. fundingId={} paymentId={} tossStatus={}",
                    fundingId, pending.getId(), lookup.get().status());
            requestFullCancel(pending, triggerType, cancelReason);
            return RefundExecutionResult.pendingPaymentUnresolved();
        }
        if (!Boolean.TRUE.equals(transactionTemplate.execute(status -> paymentRepository.failIfPending(pending.getId())))) {
            // 조회와 닫기 사이에 승인이 먼저 커밋됐다 — 결제 완료 이벤트가 order 조정 환불로 이어진다.
            log.warn("대기 결제가 그사이 다른 상태로 바뀌어 닫지 않습니다. fundingId={} paymentId={}",
                    fundingId, pending.getId());
            return RefundExecutionResult.pendingPaymentUnresolved();
        }
        log.info("결제 전 참여 취소 — 대기 결제를 실패 처리했습니다. fundingId={} paymentId={}", fundingId, pending.getId());
        return RefundExecutionResult.pendingPaymentClosed();
    }

    /**
     * PAYMENT-005/008 예외 처리 — 원 결제수단 환불이 불가하면(토스 취소 API 거절) 즉시 실패
     * 처리하지 않고 대체 계좌 입력 대기 상태로 전환한다(기능명세서 PAYMENT-005 예외 처리 항목).
     * [범위 밖] 대체 계좌를 실제로 입력받는 API는 이번 구현 범위(PaymentApiSpec.md 9개 API)에
     * 없다 — 별도 엔드포인트 신설이 필요해 보이며, PM/기획 확인이 필요하다.
     */
    public RefundExecutionResult executeFullRefundOrAwaitAlternateAccount(UUID fundingId, RefundTriggerType triggerType,
                                                                            String cancelReason) {
        Payment payment = paymentRepository.findCompletedOrCancelledByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "완료된 결제를 찾을 수 없습니다."));
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            log.info("이미 취소 처리된 결제입니다(멱등 무시). fundingId={} paymentId={}", fundingId, payment.getId());
            return RefundExecutionResult.alreadyProcessed();
        }
        RequestedCancel requested = requestFullCancel(payment, triggerType, cancelReason);
        try {
            return cancel(payment, requested);
        } catch (BusinessException e) {
            // 거절된 요청은 cancel()이 이미 대체 계좌 대기로 넘겼다(유형이 허용할 때만).
            if (e.getErrorCode() != PaymentErrorCode.PG_CANCEL_FAILED || !requested.request().canAwaitAlternateAccount()) {
                throw e;
            }
            return RefundExecutionResult.awaitingAlternateAccount();
        }
    }

    /** PAYMENT-007 — 판매자 승인에 의한 취소(전액 또는 반품비 차감 부분취소). */
    public RefundExecutionResult executeApprovedRefund(RefundRequest refundRequest, long cancelAmount,
                                                         String cancelReason) {
        Payment payment = paymentRepository.findById(refundRequest.getPaymentId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        refundRequest.startCancel(cancelAmount, cancelReason);
        RefundRequest requested = transactionTemplate.execute(status -> refundRequestRepository.save(refundRequest));
        return cancel(payment, RequestedCancel.created(requested));
    }

    /**
     * 대사 배치 — 취소를 요청한 뒤 {@code before}가 지나도록 확정되지 않은 건을 토스와 맞춘다. 한 건의 실패가
     * 나머지를 막지 않도록 건별로 처리한다.
     *
     * <p>해결하지 못한 건(판단 불가·실패)은 요청 시각을 지금으로 미뤄 목록 뒤로 보낸다. 그대로 두면 오래된 순으로
     * {@code limit}건만 가져오는 목록 앞자리를 계속 차지해, 그런 건이 {@code limit}개 쌓이면 뒤의 건이 영영 처리되지
     * 않는다.
     *
     * @return 이번 주기에 확정하거나 정리한 건수
     */
    public int reconcileCancelsRequestedBefore(Instant before, int limit) {
        int resolved = 0;
        for (RefundRequest request : refundRequestRepository.findCancelsRequestedBefore(before, limit)) {
            boolean done = false;
            try {
                done = reconcile(request);
            } catch (RuntimeException e) {
                log.warn("환불 취소 대사 실패 — 다음 주기에 다시 시도합니다. refundRequestId={}", request.getId(), e);
            }
            if (done) {
                resolved++;
            } else {
                deferQuietly(request);
            }
        }
        return resolved;
    }

    /** 미루기 실패가 배치를 멈추지 않게 한다 — 못 미룬 건은 다음 주기에 같은 자리에서 다시 시도될 뿐이다. */
    private void deferQuietly(RefundRequest request) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    refundRequestRepository.deferCancelRequest(request.getId(), Instant.now()));
        } catch (RuntimeException e) {
            log.warn("환불 취소 요청을 미루지 못했습니다. refundRequestId={}", request.getId(), e);
        }
    }

    private boolean reconcile(RefundRequest request) {
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new IllegalStateException("취소 요청의 결제가 없습니다. paymentId=" + request.getPaymentId()));
        if (payment.isPending()) {
            return reconcilePending(payment, request);
        }
        if (!payment.isCompleted()) {
            // CANCELLED는 확정 트랜잭션에서 요청과 함께 바뀌므로 여기 올 수 없다 — 사람이 봐야 한다.
            log.error("취소 요청이 남았는데 결제가 {} 상태입니다. refundRequestId={} paymentId={}",
                    payment.getStatus(), request.getId(), payment.getId());
            return false;
        }
        cancel(payment, RequestedCancel.resumed(request));
        return true;
    }

    /**
     * 결제 전 참여 취소에서 판단하지 못하고 넘어온 대기 결제(#181). 주문이 이미 취소된 결제만 여기 온다 — 결제
     * 화면에서 진행 중인 정상 결제에는 취소 요청이 없다.
     */
    private boolean reconcilePending(Payment pending, RefundRequest request) {
        Optional<TossPaymentsClient.TossPaymentLookup> lookup = tossPaymentsClient.lookupByOrderId(pending.getPgOrderId());
        if (isApproved(lookup, pending)) {
            log.warn("대기 결제가 토스에선 승인돼 있어 전액 취소합니다. refundRequestId={} paymentId={}",
                    request.getId(), pending.getId());
            markCompletedFromToss(pending, lookup.get().payment());
            transactionTemplate.executeWithoutResult(status -> paymentRepository.save(pending));
            // 대기 결제에는 취소를 부른 적이 없다 — 조회 없이 바로 취소한다.
            cancel(pending, RequestedCancel.created(request));
            return true;
        }
        if (lookup.isPresent() && !lookup.get().isNeverApprovable()) {
            return false;
        }
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (!paymentRepository.failIfPending(pending.getId())) {
                // 그사이 승인이 커밋됐다 — 다음 주기에 완료 결제로 취소한다.
                return false;
            }
            refundRequestRepository.delete(request.getId());
            log.info("승인되지 않은 대기 결제를 실패 처리하고 취소 요청을 정리했습니다. paymentId={}", pending.getId());
            return true;
        }));
    }

    private boolean isApproved(Optional<TossPaymentsClient.TossPaymentLookup> lookup, Payment pending) {
        return lookup.isPresent() && lookup.get().isDone() && lookup.get().payment().totalAmount() == pending.getAmount();
    }

    /** 주문은 이미 취소됐으니 PaymentCompleted·정산 보류 없이 완료로만 맞춘다(곧바로 전액 취소한다). */
    private void markCompletedFromToss(Payment pending, TossPaymentsClient.TossPaymentResult toss) {
        pending.markCompleted(toss.paymentKey(), toss.secret(), PaymentMethod.fromTossMethod(toss.method()),
                toss.easyPayProvider(), toss.approvedAt() == null ? Instant.now() : toss.approvedAt());
    }

    /** 트랜잭션 1 — 전액 취소 요청을 커밋한다. */
    private RequestedCancel requestFullCancel(Payment payment, RefundTriggerType triggerType, String cancelReason) {
        return transactionTemplate.execute(status -> openCancelRequest(payment, triggerType, cancelReason));
    }

    /**
     * 이미 진행 중인 취소 요청이 있으면 이어받는다(이벤트 중복 수신·재시도) — 새로 만들면 같은 결제를 두 번 취소한다.
     * 방금 요청된 건이면 다른 호출이 아직 토스 응답을 기다리는 중일 수 있어 손대지 않는다({@link #cancel}).
     */
    private RequestedCancel openCancelRequest(Payment payment, RefundTriggerType triggerType, String cancelReason) {
        return refundRequestRepository.findCancelInFlightByPaymentId(payment.getId())
                .map(existing -> isStale(existing) ? RequestedCancel.resumed(existing) : RequestedCancel.inFlight(existing))
                .orElseGet(() -> RequestedCancel.created(refundRequestRepository.save(RefundRequest.requestCancel(
                        triggerType, payment.getFundingId(), payment.getId(), payment.getAmount(), cancelReason))));
    }

    private boolean isStale(RefundRequest request) {
        return request.getCancelRequestedAt() == null
                || request.getCancelRequestedAt().isBefore(Instant.now().minus(staleAfter));
    }

    /**
     * 토스 취소(트랜잭션 밖) → 확정(트랜잭션 2). 이어받은 요청이면 먼저 토스 조회로 이미 일어난 취소를 찾는다 —
     * 다시 부르면 부분취소(반품비 차감)는 한 번 더 실행될 수 있다.
     *
     * <p>토스 5xx·타임아웃({@link DependencyFailureException})은 그대로 던진다. 요청은 PROCESSING으로 남아 대사
     * 배치가 결과를 맞춘다.
     */
    private RefundExecutionResult cancel(Payment payment, RequestedCancel requested) {
        RefundRequest request = requested.request();
        if (requested.inFlight()) {
            // 같은 결제를 동시에 두 번 취소하지 않는다. 앞선 호출이 확정하거나, 멈췄으면 대사 배치가 맞춘다.
            log.info("진행 중인 취소 요청이 있어 토스를 다시 부르지 않습니다. refundRequestId={} paymentId={}",
                    request.getId(), payment.getId());
            return RefundExecutionResult.inProgress(request, payment);
        }
        TossPaymentsClient.TossCancelResult result = requested.resumed()
                ? findUnrecordedCancel(payment, request).orElse(null) : null;
        if (result == null) {
            result = callCancel(payment, request);
        }
        return complete(payment, request, result);
    }

    private TossPaymentsClient.TossCancelResult callCancel(Payment payment, RefundRequest request) {
        try {
            return tossPaymentsClient.cancel(payment.getPgPaymentKey(), request.getCancelAmount(), request.getCancelReason());
        } catch (TossApiException e) {
            if (e.isAlreadyCanceled()) {
                // 앞선 취소가 성공했는데 확정만 못 했다 — 실패가 아니다. 조회로 그 취소를 찾아 확정한다.
                return findUnrecordedCancel(payment, request).orElseThrow(() -> new DependencyFailureException(e));
            }
            rejectCancel(payment, request);
            throw new BusinessException(PaymentErrorCode.PG_CANCEL_FAILED, e.getTossMessage());
        }
    }

    /** 토스에 있는 취소 중 로컬 취소 내역에 아직 없는 것(요청 금액과 같은 것) — 확정하지 못한 취소다. */
    private Optional<TossPaymentsClient.TossCancelResult> findUnrecordedCancel(Payment payment, RefundRequest request) {
        return tossPaymentsClient.lookup(payment.getPgPaymentKey()).cancels().stream()
                .filter(cancel -> cancel.cancelAmount() == request.getCancelAmount())
                .filter(cancel -> !paymentCancellationJpaRepository.existsByPgTransactionKey(cancel.transactionKey()))
                .findFirst();
    }

    /**
     * 토스가 취소를 거절했다(4xx) — 돈이 움직이지 않았으니 요청을 되돌린다. 되돌리는 방식은 유형마다 다르다:
     * 대체 계좌 대기(005/008), 판매자 재검토(007), 요청 삭제(004/017 — 예전 롤백과 같은 결과).
     */
    private void rejectCancel(Payment payment, RefundRequest request) {
        transactionTemplate.executeWithoutResult(status -> {
            if (request.canAwaitAlternateAccount()) {
                log.warn("원 결제수단 환불 실패 — 대체 계좌 입력 대기 상태로 전환합니다. fundingId={} refundRequestId={}",
                        payment.getFundingId(), request.getId());
                request.awaitAlternateAccount();
                refundRequestRepository.save(request);
                paymentNotificationPublisher.publishRefundStatusChanged(new RefundStatusChangedEvent(
                        payment.getFundingId(), payment.getMemberId(), RefundNotificationStatus.AWAITING_ALTERNATE_ACCOUNT));
            } else if (request.getTriggerType().isSellerDecisionTarget()) {
                request.revertCancel();
                refundRequestRepository.save(request);
            } else {
                refundRequestRepository.delete(request.getId());
            }
        });
    }

    /** 트랜잭션 2 — 토스 취소 결과를 로컬에 한 번에 확정한다. */
    private RefundExecutionResult complete(Payment payment, RefundRequest request,
                                            TossPaymentsClient.TossCancelResult cancelResult) {
        return transactionTemplate.execute(status -> {
            boolean isFullRefund = request.getCancelAmount() >= payment.getAmount();
            if (isFullRefund) {
                payment.markCancelled();
                paymentRepository.save(payment);
                // 전액 환불 시에만 에스크로 보류를 해제한다 — 부분취소(반품비 차감)는 나머지 금액이
                // 여전히 정산 대상이므로 HOLDING을 유지한다.
                settlementHoldService.releaseToRefund(payment.getId());
            }

            paymentCancellationJpaRepository.save(PaymentCancellationJpaEntity.builder()
                    .paymentId(payment.getId())
                    .refundRequestId(request.getId())
                    .pgTransactionKey(cancelResult.transactionKey())
                    .cancelAmount(request.getCancelAmount())
                    .cancelReason(request.getCancelReason())
                    .canceledAt(cancelResult.canceledAt() == null ? Instant.now() : cancelResult.canceledAt())
                    .build());

            request.completeCancel(isFullRefund);
            RefundRequest saved = refundRequestRepository.save(request);

            paymentEventPublisher.publishRefundCompleted(new PaymentEventPublisher.RefundCompletedEvent(
                    payment.getId(), payment.getFundingId(), payment.getCouponIssuanceIds(),
                    request.getTriggerType().toOrderServiceReason(), isFullRefund));
            paymentNotificationPublisher.publishRefundStatusChanged(new RefundStatusChangedEvent(
                    payment.getFundingId(), payment.getMemberId(), RefundNotificationStatus.COMPLETED));

            return new RefundExecutionResult(saved.getId(), saved.getStatus().name(), isFullRefund);
        });
    }

    /**
     * @param resumed  이미 있던 요청을 이어받았는지 — 그렇다면 토스에서 취소가 이미 일어났을 수 있다
     * @param inFlight 방금 요청된 건이라 다른 호출이 처리 중으로 보는지 — 그렇다면 토스를 부르지 않는다
     */
    private record RequestedCancel(RefundRequest request, boolean resumed, boolean inFlight) {
        static RequestedCancel created(RefundRequest request) {
            return new RequestedCancel(request, false, false);
        }

        static RequestedCancel resumed(RefundRequest request) {
            return new RequestedCancel(request, true, false);
        }

        static RequestedCancel inFlight(RefundRequest request) {
            return new RequestedCancel(request, true, true);
        }
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

        /** 다른 호출이 같은 취소를 처리 중이다 — 확정은 그 호출이나 대사 배치가 한다. */
        static RefundExecutionResult inProgress(RefundRequest request, Payment payment) {
            return new RefundExecutionResult(request.getId(), "PROCESSING",
                    request.getCancelAmount() >= payment.getAmount());
        }

        static RefundExecutionResult pendingPaymentUnresolved() {
            return new RefundExecutionResult(null, "PENDING_PAYMENT_UNRESOLVED", false);
        }
    }
}
