package com.fundit.payment.domain.payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByPgOrderId(String pgOrderId);

    Optional<Payment> findByPgPaymentKey(String pgPaymentKey);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /** payments.completed_funding_id 유니크 인덱스 기준 — 펀딩당 완료 결제는 최대 1건. */
    Optional<Payment> findCompletedByFundingId(Long fundingId);

    /**
     * PAYMENT-004/005/017 — 취소 실행 대상 조회. 이벤트 중복 수신 멱등 처리를 위해 이미
     * CANCELLED가 된 건도 함께 찾을 수 있어야 한다(completed_funding_id 생성 컬럼은
     * CANCELLED가 되는 순간 NULL로 빠지므로 {@link #findCompletedByFundingId}만으로는 부족).
     */
    Optional<Payment> findCompletedOrCancelledByFundingId(Long fundingId);

    /** PAYMENT-001 ⑤ — 이미 pg_order_id가 발급된 시도가 있으면 재사용(중복 생성 방지). */
    Optional<Payment> findPendingByFundingId(Long fundingId);

    boolean existsByPgOrderId(String pgOrderId);
}
