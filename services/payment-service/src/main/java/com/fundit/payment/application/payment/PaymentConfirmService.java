package com.fundit.payment.application.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * PAYMENT-002 — 결제 승인 처리. amount는 PAYMENT-001 스냅샷과만 대조하고 재계산하지 않는다
 * (order-service 재조회 없음 — payment-service CLAUDE.md "절대 하지 말아야 할 것").
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentEventPublisher paymentEventPublisher;
    private final SettlementHoldService settlementHoldService;

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

        // 정산 에스크로 보류 시작 — 결제 완료 즉시 정산 대상 금액을 잡아둔다(settlement.settlement_holds).
        settlementHoldService.openHold(saved.getId(), saved.getFundingId(), saved.getAmount());

        // ⑤ 같은 트랜잭션에서 아웃박스 적재 — 별도 트랜잭션으로 분리하지 않는다(CLAUDE.md "절대 하지 말아야 할 것")
        paymentEventPublisher.publishPaymentCompleted(new PaymentEventPublisher.PaymentCompletedEvent(
                saved.getId(), saved.getFundingId(), saved.getCouponIssuanceId(), saved.getPaidAt()));

        return PaymentConfirmResult.from(saved);
    }

    private TossPaymentsClient.TossPaymentResult confirmWithToss(Payment payment, String paymentKey, String orderId,
                                                                   long amount) {
        try {
            return tossPaymentsClient.confirm(paymentKey, orderId, amount);
        } catch (TossApiException e) {
            // ⑥ 실패: Payment(FAILED)만 기록하고 Funding.status는 손대지 않는다(order-service가 PENDING 유지)
            payment.markFailed();
            paymentRepository.save(payment);
            if (e.isSessionExpired()) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_EXPIRED, e.getTossMessage());
            }
            throw new BusinessException(PaymentErrorCode.PG_CONFIRM_FAILED, e.getTossMessage());
        }
    }

    public record PaymentConfirmResult(UUID paymentId, Long fundingId, String status, PaymentMethod paymentMethod,
                                        String easyPayProvider, Instant paidAt) {
        static PaymentConfirmResult from(Payment payment) {
            return new PaymentConfirmResult(payment.getId(), payment.getFundingId(), payment.getStatus().name(),
                    payment.getPaymentMethod(), payment.getEasyPayProvider(), payment.getPaidAt());
        }
    }
}
