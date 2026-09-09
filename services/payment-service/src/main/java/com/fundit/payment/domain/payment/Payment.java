package com.fundit.payment.domain.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.payment.domain.PaymentErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — 상태 전이 규칙(PENDING → COMPLETED/FAILED/CANCELLED)과
 * 위변조 방지를 위한 금액 대조 불변식을 가진다.
 *
 * <p>{@code amount}/{@code orderName}은 PAYMENT-001 시점 order-service 스냅샷으로 고정되며,
 * 이후 절대 재계산하지 않는다(payment-service CLAUDE.md "핵심 설계 결정" 참고).
 */
@Getter
@Builder(toBuilder = true)
public class Payment {

    private final UUID id;
    private final Long fundingId;
    private final UUID memberId;
    private final String pgOrderId;
    private String pgPaymentKey;
    private String pgSecret;
    private final long amount;
    private final String orderName;
    /** order-service {@code coupon_issuances.id} 참조, FK 아님. 쿠폰 미적용 주문이면 null. */
    private final Long couponIssuanceId;
    private PaymentMethod paymentMethod;
    private String easyPayProvider;
    private PaymentStatus status;
    private final String idempotencyKey;
    private Instant paidAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public static Payment create(Long fundingId, UUID memberId, String pgOrderId, long amount, String orderName,
                                  Long couponIssuanceId, String idempotencyKey) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .fundingId(fundingId)
                .memberId(memberId)
                .pgOrderId(pgOrderId)
                .amount(amount)
                .orderName(orderName)
                .couponIssuanceId(couponIssuanceId)
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    /** PAYMENT-002 ② — 요청 amount를 PAYMENT-001 스냅샷과만 대조한다(재계산 금지). */
    public void verifyAmount(long requestedAmount) {
        if (this.amount != requestedAmount) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    public void assertPending() {
        if (status != PaymentStatus.PENDING) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_PENDING);
        }
    }

    /** PAYMENT-002 ④ — 토스 승인 성공 반영. */
    public void markCompleted(String pgPaymentKey, String pgSecret, PaymentMethod paymentMethod,
                               String easyPayProvider, Instant paidAt) {
        assertPending();
        this.pgPaymentKey = pgPaymentKey;
        this.pgSecret = pgSecret;
        this.paymentMethod = paymentMethod;
        this.easyPayProvider = easyPayProvider;
        this.paidAt = paidAt;
        this.status = PaymentStatus.COMPLETED;
    }

    /** PAYMENT-002 ⑥ — 승인 실패. Funding.status는 이 서비스가 건드리지 않는다(order-service가 PENDING 유지). */
    public void markFailed() {
        assertPending();
        this.status = PaymentStatus.FAILED;
    }

    /** PAYMENT-004/005/007/008/017 — 토스 전액 취소 완료 반영. */
    public void markCancelled() {
        if (status != PaymentStatus.COMPLETED) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_PENDING, "완료된 결제만 취소할 수 있습니다.");
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }

    public boolean isOwnedBy(UUID accountId) {
        return memberId.equals(accountId);
    }
}
