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
     * (재시도하면 토스가 이미 승인된 건이라 거절한다). 상태 전이(PROCESSING)는 결제와 같은
     * 트랜잭션에서 커밋하고, 호출 실패는 로그만 남긴다 — fulfillment 쪽이 refundRequestId로
     * 멱등이라 같은 요청을 다시 보내면 된다.
     *
     * <p>ponytail: 재시도는 아직 수동이다(PROCESSING인 교환 건에 같은 내부 API를 다시 호출).
     * 실패가 실제로 관측되면 아웃박스(payment_event_outbox와 같은 패턴)로 옮길 것.
     */
    @Override
    @Transactional
    public void onExchangeFeePaid(Payment payment) {
        RefundRequest refundRequest = refundRequestRepository.findById(payment.getRefundRequestId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        refundRequest.startExchangeReshipment();
        RefundRequest saved = refundRequestRepository.save(refundRequest);
        afterCommit(() -> requestReshipmentQuietly(saved));
    }

    /**
     * 재발송분이 발송되면(판매자가 새 운송장을 등록해 PREPARING→SHIPPED) 교환 신청을 종료한다.
     * 최초 발송은 교환 신청 전에 이미 지나갔으므로, 재발송 요청 상태(PROCESSING)로 남아 있는
     * 교환 건에 도착한 발송 이벤트는 재발송분의 것이다. 해당 교환 건이 없으면 아무것도 하지
     * 않는다(일반 주문의 최초 발송 이벤트).
     *
     * <p>완료 기준을 배송완료가 아니라 발송으로 두는 이유: {@code shipping.completed.v1}은 아직
     * 발행 주체가 없고 payload가 레거시 Long fundingId다({@code ShippingCompletionListener} 주석).
     * ponytail: 그 이벤트가 UUID로 실제 발행되면 완료 기준을 배송완료로 옮길 수 있다.
     */
    @Transactional
    public void onReshipmentShipped(UUID orderId) {
        Optional<RefundRequest> inProgress = refundRequestRepository.findReshippingExchangeByFundingId(orderId);
        if (inProgress.isEmpty()) {
            return;
        }
        RefundRequest refundRequest = inProgress.get();
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
        return saved;
    }

    private void requestReshipmentQuietly(RefundRequest refundRequest) {
        try {
            exchangeReshipmentClient.request(refundRequest.getFundingId(), refundRequest.getId());
        } catch (RuntimeException e) {
            log.error("교환 재발송 요청 실패 — 교환비 결제는 완료됐으므로 재시도가 필요하다. refundRequestId={}, orderId={}",
                    refundRequest.getId(), refundRequest.getFundingId(), e);
        }
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
