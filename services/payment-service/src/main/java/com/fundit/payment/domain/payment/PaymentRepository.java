package com.fundit.payment.domain.payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByPgOrderId(String pgOrderId);

    Optional<Payment> findByPgPaymentKey(String pgPaymentKey);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /** payments.completed_funding_order_id 유니크 인덱스 기준 — 펀딩당 완료 결제는 최대 1건. */
    Optional<Payment> findCompletedByFundingId(UUID fundingId);

    /**
     * PAYMENT-004/005/017 — 취소 실행 대상 조회. 이벤트 중복 수신 멱등 처리를 위해 이미
     * CANCELLED가 된 건도 함께 찾을 수 있어야 한다(completed_funding_order_id 생성 컬럼은
     * CANCELLED가 되는 순간 NULL로 빠지므로 {@link #findCompletedByFundingId}만으로는 부족).
     */
    Optional<Payment> findCompletedOrCancelledByFundingId(UUID fundingId);

    /**
     * 정산 스케줄이 아직 order-service 내부 PK(Long)를 들고 있을 때 쓰는 레거시 조회.
     * UUID 전환 범위 밖(settlement)이라 내부 PK 컬럼으로만 찾는다.
     */
    Optional<Payment> findCompletedOrCancelledByInternalFundingId(Long internalFundingId);

    /** PAYMENT-001 ⑤ — 이미 pg_order_id가 발급된 시도가 있으면 재사용(중복 생성 방지). */
    Optional<Payment> findPendingByFundingId(UUID fundingId);

    boolean existsByPgOrderId(String pgOrderId);
}
