package com.fundit.payment.infrastructure.persistence.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByPgOrderId(String pgOrderId);

    Optional<PaymentJpaEntity> findByPgPaymentKey(String pgPaymentKey);

    Optional<PaymentJpaEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * 펀딩 단위 조회는 모두 리워드 결제만 대상이다 — 같은 펀딩에 교환 배송비 결제(purpose=
     * 'EXCHANGE_FEE')가 함께 있을 수 있어, purpose를 걸지 않으면 5,000원 교환비 결제가 원 결제로
     * 잡혀 환불이 그 금액만 취소하거나 리워드 결제 시도가 교환비 시도를 재사용한다.
     */
    Optional<PaymentJpaEntity> findByCompletedFundingOrderIdAndPurpose(UUID completedFundingOrderId, String purpose);

    Optional<PaymentJpaEntity> findFirstByFundingOrderIdAndPurposeAndStatusOrderByCreatedAtDesc(UUID fundingOrderId,
                                                                                               String purpose,
                                                                                               String status);

    Optional<PaymentJpaEntity> findFirstByFundingOrderIdAndPurposeAndStatusInOrderByCreatedAtDesc(UUID fundingOrderId,
                                                                                                 String purpose,
                                                                                                 List<String> statuses);

    /** 교환비 결제는 교환 신청 단위다 — 같은 신청에 재시도가 쌓일 수 있어 최신 건을 본다. */
    Optional<PaymentJpaEntity> findFirstByRefundRequestIdOrderByCreatedAtDesc(Long refundRequestId);

    /** 정산 스케줄(BIGINT funding_id) 전용 — 레거시 내부 PK 컬럼 조회. */
    Optional<PaymentJpaEntity> findFirstByFundingIdAndStatusInOrderByCreatedAtDesc(Long fundingId, List<String> statuses);

    boolean existsByPgOrderId(String pgOrderId);
}
