package com.fundit.payment.infrastructure.persistence.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByPgOrderId(String pgOrderId);

    Optional<PaymentJpaEntity> findByPgPaymentKey(String pgPaymentKey);

    Optional<PaymentJpaEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentJpaEntity> findByCompletedFundingId(Long completedFundingId);

    Optional<PaymentJpaEntity> findFirstByFundingIdAndStatusOrderByCreatedAtDesc(Long fundingId, String status);

    Optional<PaymentJpaEntity> findFirstByFundingIdAndStatusInOrderByCreatedAtDesc(Long fundingId, List<String> statuses);

    boolean existsByPgOrderId(String pgOrderId);
}
