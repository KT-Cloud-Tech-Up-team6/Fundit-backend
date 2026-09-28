package com.fundit.payment.application.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PAYMENT-002 — 결제 승인 처리. amount는 PAYMENT-001 스냅샷과만 대조하고 재계산하지 않는다
 * (order-service 재조회 없음 — payment-service CLAUDE.md "절대 하지 말아야 할 것").
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmService.class);

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentEventPublisher paymentEventPublisher;
    private final SettlementHoldService settlementHoldService;
    private final PaymentFailureRecorder paymentFailureRecorder;
    private final ExchangeFeePaymentListener exchangeFeePaymentListener;

    @Transactional
    public PaymentConfirmResult confirm(UUID accountId, String paymentKey, String orderId, long amount) {
        Payment payment = paymentRepository.findByPgOrderId(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!payment.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        // 멱등 재시도 — 이미 같은 paymentKey로 승인 완료된 요청이면 토스 재호출 없이 기존 결과를 반환한다
        // (idempotency_key의 취지: 승인 API 재시도 시 중복 승인 방지, ERD 3장 코멘트).
        if (payment.isCompleted() && paymentKey.equals(payment.getPgPaymentKey())) {
            return PaymentConfirmResult.from(payment);
        }

        payment.assertPending();
        // ② amount 대조 — 불일치 시 승인 API 호출 자체를 막는다
        payment.verifyAmount(amount);

        TossPaymentsClient.TossPaymentResult tossResult = confirmWithToss(payment, paymentKey, orderId, amount);

        PaymentMethod method = PaymentMethod.fromTossMethod(tossResult.method());
        payment.markCompleted(tossResult.paymentKey(), tossResult.secret(), method, tossResult.easyPayProvider(),
                tossResult.approvedAt() == null ? Instant.now() : tossResult.approvedAt());
        Payment saved = paymentRepository.save(payment);

        // 교환 배송비는 주문 결제가 아니다 — order-service에 알릴 PaymentCompleted도, 판매자 정산
        // 대상 금액도 아니라서(정산 귀속 미정, 아래 ponytail) 교환 흐름으로만 넘긴다.
        // ponytail: 교환비를 판매자 정산에 포함하기로 정해지면 여기서 openHold를 열면 된다.
        if (saved.isExchangeFee()) {
            exchangeFeePaymentListener.onExchangeFeePaid(saved);
            return PaymentConfirmResult.from(saved);
        }

        // 정산 에스크로 보류 시작 — 결제 완료 즉시 정산 대상 금액을 잡아둔다(settlement.settlement_holds).
        // settlement_holds.funding_id는 아직 BIGINT(정산 UUID 전환 범위 밖)라 신규 결제는 null로 둔다.
        settlementHoldService.openHold(saved.getId(), null, saved.getAmount());

        // ⑤ 같은 트랜잭션에서 아웃박스 적재 — 별도 트랜잭션으로 분리하지 않는다(CLAUDE.md "절대 하지 말아야 할 것")
        paymentEventPublisher.publishPaymentCompleted(new PaymentEventPublisher.PaymentCompletedEvent(
                saved.getId(), saved.getFundingId(), saved.getCouponIssuanceIds(), saved.getPaidAt()));

        return PaymentConfirmResult.from(saved);
    }

    private TossPaymentsClient.TossPaymentResult confirmWithToss(Payment payment, String paymentKey, String orderId,
                                                                   long amount) {
        try {
            return tossPaymentsClient.confirm(paymentKey, orderId, amount);
        } catch (DependencyFailureException e) {
            // 5xx·타임아웃 — 토스에서는 승인됐을 수 있다. 조회로 승인이 확인되면 완료로 맞추고,
            // 아니면 그대로 던져 트랜잭션을 롤백한다(결제는 PENDING 유지, 재확정 가능).
            return findApproved(paymentKey, orderId, amount).orElseThrow(() -> e);
        } catch (TossApiException e) {
            if (e.isAlreadyProcessed()) {
                // 결과 불명 뒤 재확정 — 이미 승인된 결제면 완료로 맞춘다. 조회로 확인하지 못하면 토스에선 승인됐을 수
                // 있으니 FAILED로 굳히지 않고 결과 불명(503)으로 던진다(PENDING 유지, 재확정 가능).
                return findApproved(paymentKey, orderId, amount).orElseThrow(() -> new DependencyFailureException(e));
            }
            // ⑥ 실패: Payment(FAILED)만 기록하고 Funding.status는 손대지 않는다(order-service가 PENDING 유지).
            // 이 예외가 던져지면 confirm()의 트랜잭션 전체가 롤백되므로, FAILED 기록은 별도 트랜잭션에서 커밋한다.
            paymentFailureRecorder.recordFailure(payment);
            // 토스 오류 코드는 detail로 내린다 — FE가 거절 사유별 안내를 분기할 수 있게(메시지 파싱 금지).
            Map<String, String> detail = Map.of("tossErrorCode", e.getTossErrorCode());
            if (e.isSessionExpired()) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_EXPIRED, e.getTossMessage(), detail);
            }
            throw new BusinessException(PaymentErrorCode.PG_CONFIRM_FAILED, e.getTossMessage(), detail);
        }
    }

    /**
     * 토스에 실제로 승인됐는지 조회로 대조한다. 주문번호·금액까지 맞아야 인정한다 — 다른 주문의 paymentKey로
     * 완료 처리되는 일을 막는다. 조회 자체가 실패하면 판단하지 않는다(빈 값).
     */
    private Optional<TossPaymentsClient.TossPaymentResult> findApproved(String paymentKey, String orderId, long amount) {
        try {
            TossPaymentsClient.TossPaymentLookup lookup = tossPaymentsClient.lookup(paymentKey);
            TossPaymentsClient.TossPaymentResult result = lookup.payment();
            if (lookup.isDone() && orderId.equals(result.orderId()) && result.totalAmount() == amount) {
                return Optional.of(result);
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("토스 결제 조회 실패, 승인 여부를 판단하지 않습니다. orderId={}", orderId, e);
            return Optional.empty();
        }
    }

    public record PaymentConfirmResult(UUID paymentId, UUID fundingId, String status, PaymentMethod paymentMethod,
                                        String easyPayProvider, Instant paidAt) {
        static PaymentConfirmResult from(Payment payment) {
            return new PaymentConfirmResult(payment.getId(), payment.getFundingId(), payment.getStatus().name(),
                    payment.getPaymentMethod(), payment.getEasyPayProvider(), payment.getPaidAt());
        }
    }
}
