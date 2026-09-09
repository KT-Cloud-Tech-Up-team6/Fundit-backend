package com.fundit.payment.infrastructure.persistence.payment;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentMapper mapper;

    @Override
    public Payment save(Payment payment) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(payment)));
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByPgOrderId(String pgOrderId) {
        return jpaRepository.findByPgOrderId(pgOrderId).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByPgPaymentKey(String pgPaymentKey) {
        return jpaRepository.findByPgPaymentKey(pgPaymentKey).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findCompletedByFundingId(Long fundingId) {
        return jpaRepository.findByCompletedFundingId(fundingId).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findCompletedOrCancelledByFundingId(Long fundingId) {
        return jpaRepository.findFirstByFundingIdAndStatusInOrderByCreatedAtDesc(fundingId,
                        List.of(PaymentStatus.COMPLETED.name(), PaymentStatus.CANCELLED.name()))
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findPendingByFundingId(Long fundingId) {
        return jpaRepository.findFirstByFundingIdAndStatusOrderByCreatedAtDesc(fundingId, PaymentStatus.PENDING.name())
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByPgOrderId(String pgOrderId) {
        return jpaRepository.existsByPgOrderId(pgOrderId);
    }
}
