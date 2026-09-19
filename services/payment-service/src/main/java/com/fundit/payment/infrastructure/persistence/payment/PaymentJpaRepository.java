package com.fundit.payment.infrastructure.persistence.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByPgOrderId(String pgOrderId);

    Optional<PaymentJpaEntity> findByPgPaymentKey(String pgPaymentKey);

    Optional<PaymentJpaEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentJpaEntity> findByCompletedFundingOrderId(UUID completedFundingOrderId);

    Optional<PaymentJpaEntity> findFirstByFundingOrderIdAndStatusOrderByCreatedAtDesc(UUID fundingOrderId, String status);

    Optional<PaymentJpaEntity> findFirstByFundingOrderIdAndStatusInOrderByCreatedAtDesc(UUID fundingOrderId,
                                                                                       List<String> statuses);

    /** 정산 스케줄(BIGINT funding_id) 전용 — 레거시 내부 PK 컬럼 조회. */
    Optional<PaymentJpaEntity> findFirstByFundingIdAndStatusInOrderByCreatedAtDesc(Long fundingId, List<String> statuses);

    boolean existsByPgOrderId(String pgOrderId);
}
