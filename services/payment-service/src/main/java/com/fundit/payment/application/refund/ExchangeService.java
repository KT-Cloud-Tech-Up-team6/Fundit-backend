package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.payment.ExchangeFeePaymentListener;
import com.fundit.payment.application.reshipment.ExchangeReshipmentClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.refund.ExchangeReason;
import com.fundit.payment.domain.refund.RefundReasonTag;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 교환 신청의 승인 이후 흐름 — 결제취소가 아니라 <b>교환비 수납 + 재발송</b>으로 끝나기 때문에
 * 하자환불·반품(RefundExecutionService)과 경로가 완전히 다르다.
 *
 * <pre>
 * REQUESTED ──판매자 승인──┬─ 구매자 귀책 → APPROVED (교환비 결제 대기)
 *                          │        └─ 교환비 결제 승인 → PROCESSING (재발송 요청됨)
 *                          └─ 판매자 귀책·기타(0원) → PROCESSING (즉시 재발송 요청)
 * PROCESSING ──재발송분 발송(운송장 등록) 이벤트──> COMPLETED
 * </pre>
 *
 * <p>교환비를 승인 시점에 받는 이유: 부담 주체가 판매자 검토 결과(귀책)로 정해지므로 신청
 * 시점에 받으면 귀책이 뒤집힐 때마다 환불 경로가 필요해진다(환불 정책 V.1.0).
 */
@Service
@RequiredArgsConstructor
public class ExchangeService implements ExchangeFeePaymentListener {

    private static final Logger log = LoggerFactory.getLogger(ExchangeService.class);

    private final RefundRequestRepository refundRequestRepository;
    private final ExchangeReshipmentClient exchangeReshipmentClient;

    /**
     * 판매자 승인 — 호출 전에 판매자 소유권이 검증됐다고 전제한다(PostShipmentDecision 경로에서
     * 이미 확인). 구매자 귀책이면 교환비 결제를 기다리고, 그 외에는 곧바로 재발송을 요청한다.
     */
    @Transactional
    public ExchangeApprovalResult approve(RefundRequest refundRequest) {
        long additionalPaymentAmount = exchangeReasonOf(refundRequest).additionalPaymentAmount();
        if (additionalPaymentAmount > 0) {
            refundRequest.approveExchangeAwaitingFee();
            RefundRequest saved = refundRequestRepository.save(refundRequest);
            return new ExchangeApprovalResult(saved.getId(), saved.getStatus().name(), additionalPaymentAmount);
        }
        return new ExchangeApprovalResult(requestReshipment(refundRequest).getId(),
                refundRequest.getStatus().name(), 0L);
    }

    /**
     * 교환비 결제 승인 직후(PAYMENT-002 용도 분기) — 결제가 끝났으니 재발송을 요청한다.
     *
     * <p>재발송 호출은 <b>결제 커밋 이후</b>에 한다. 승인 트랜잭션 안에서 부르면 fulfillment
     * 장애가 이미 토스에서 승인된 결제를 롤백시켜, 돈은 빠졌는데 결제 기록이 PENDING으로 남는다
     * (재시도하면 토스가 이미 승인된 건이라 거절한다).
     *
     * <p>호출이 실패해도 요청은 유실되지 않는다 — 상태 전이(PROCESSING)는 결제와 같은 트랜잭션에서 커밋되고
     * {@code reshipment_requested_at}이 null로 남아 {@link #retryPendingReshipmentRequests}가
     * 같은 요청을 다시 보낸다(fulfillment는 refundRequestId로 멱등). 별도 아웃박스 테이블을 두지
     * 않은 이유는 이 행 자체가 이미 작업 단위이기 때문이다(V10 주석).
     */
    @Override
    @Transactional
    public void onExchangeFeePaid(Payment payment) {
        RefundRequest refundRequest = refundRequestRepository.findById(payment.getRefundRequestId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        refundRequest.startExchangeReshipment();
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        afterCommit(() -> requestReshipmentQuietly(saved.getId()));
    }

    /**
     * 재발송 요청이 성공하지 못한 교환 건을 다시 보낸다(스케줄러가 주기적으로 호출). 교환비는
     * 이미 결제됐으므로 요청이 유실되면 구매자가 돈만 낸 상태로 멈춘다 — 그래서 로그 경고로
     * 끝내지 않고 이 경로로 계속 재시도한다. 한 건이 실패해도 나머지는 계속 처리한다.
     */
    public int retryPendingReshipmentRequests(int limit) {
        List<RefundRequest> pending = refundRequestRepository.findExchangesAwaitingReshipmentRequest(limit);
        int requested = 0;
        for (RefundRequest refundRequest : pending) {
            if (requestReshipmentQuietly(refundRequest.getId())) {
                requested++;
            }
        }
        return requested;
    }

    /**
     * 재발송분이 발송되면(판매자가 새 운송장을 등록해 PREPARING→SHIPPED) 교환 신청을 종료한다.
     *
     * <p>이벤트가 들고 오는 {@code reshipmentRefundRequestId}(fulfillment의
     * {@code shipments.last_reshipment_refund_request_id})가 이 교환 건의 id와 같을 때만 완료
     * 처리한다. 값이 없으면(최초 발송) 또는 다른 교환 건의 재발송이면 무시한다 — Kafka는
     * at-least-once라서 최초 발송 이벤트가 나중에 재전달되면 교환이 실제 재발송 전에 완료로
     * 넘어갈 수 있다.
     *
     * <p>완료 기준을 배송완료가 아니라 발송으로 두는 이유: {@code shipping.completed.v1}은 아직
     * 발행 주체가 없고 payload가 레거시 Long fundingId다({@code ShippingCompletionListener} 주석).
     * ponytail: 그 이벤트가 UUID로 실제 발행되면 완료 기준을 배송완료로 옮길 수 있다.
     */
    @Transactional
    public void onReshipmentShipped(UUID orderId, Long reshipmentRefundRequestId) {
        if (reshipmentRefundRequestId == null) {
            return;
        }
        Optional<RefundRequest> inProgress = refundRequestRepository.findReshippingExchangeByFundingId(orderId);
        if (inProgress.isEmpty()) {
            return;
        }
        RefundRequest refundRequest = inProgress.get();
        if (!reshipmentRefundRequestId.equals(refundRequest.getId())) {
            log.warn("재발송 식별자가 진행 중인 교환 건과 다르다 — 무시한다. orderId={}, 이벤트={}, 교환신청={}",
                    orderId, reshipmentRefundRequestId, refundRequest.getId());
            return;
        }
        refundRequest.completeExchange();
        refundRequestRepository.save(refundRequest);
    }

    /**
     * 판매자 승인 경로의 재발송 요청 — 여기서는 호출 실패가 트랜잭션을 롤백시키는 것이 맞다.
     * 돈이 움직이지 않았고, 판매자가 승인 실패를 보고 다시 누르면 된다(멱등).
     */
    private RefundRequest requestReshipment(RefundRequest refundRequest) {
        refundRequest.startExchangeReshipment();
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        exchangeReshipmentClient.request(saved.getFundingId(), saved.getId());
        saved.markReshipmentRequested();
        return refundRequestRepository.save(saved);
    }

    /**
     * 실패를 삼키되 {@code reshipment_requested_at}을 채우지 않아 재시도 대상으로 남긴다.
     * 이 경로는 결제 커밋 이후(또는 재시도 스케줄러)에서 돌아 바깥 트랜잭션이 없고, 조회·저장이
     * 각각 리포지토리 자체 트랜잭션으로 커밋된다(한 행 저장이라 원자성을 더 묶을 게 없다).
     */
    private boolean requestReshipmentQuietly(Long refundRequestId) {
        RefundRequest refundRequest = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        try {
            exchangeReshipmentClient.request(refundRequest.getFundingId(), refundRequest.getId());
        } catch (RuntimeException e) {
            log.error("교환 재발송 요청 실패 — 교환비 결제는 완료됐으므로 다음 주기에 재시도한다. refundRequestId={}, orderId={}",
                    refundRequest.getId(), refundRequest.getFundingId(), e);
            return false;
        }
        refundRequest.markReshipmentRequested();
        refundRequestRepository.save(refundRequest);
        return true;
    }

    /** 트랜잭션이 없으면(단위 테스트 등) 그대로 실행한다. */
    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /** 사유는 접수 시 {@code [CHANGE_OF_MIND] 상세} 형태로 저장돼 있어 태그에서 되읽는다. */
    private ExchangeReason exchangeReasonOf(RefundRequest refundRequest) {
        return ExchangeReason.orOther(RefundReasonTag.parse(refundRequest.getReasonDetail()).reasonType());
    }

    /** {@code additionalPaymentAmount}가 0보다 크면 구매자가 교환비를 결제해야 재발송이 시작된다. */
    public record ExchangeApprovalResult(Long refundId, String status, long additionalPaymentAmount) {
    }
}
