package com.fundit.payment.infrastructure.persistence.payment;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentPurpose;
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
    public Optional<Payment> findCompletedByFundingId(UUID fundingId) {
        return jpaRepository.findByCompletedFundingOrderIdAndPurpose(fundingId, PaymentPurpose.REWARD.name())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findCompletedOrCancelledByFundingId(UUID fundingId) {
        return jpaRepository.findFirstByFundingOrderIdAndPurposeAndStatusInOrderByCreatedAtDesc(fundingId,
                        PaymentPurpose.REWARD.name(),
                        List.of(PaymentStatus.COMPLETED.name(), PaymentStatus.CANCELLED.name()))
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findCompletedOrCancelledByInternalFundingId(Long internalFundingId) {
        return jpaRepository.findFirstByFundingIdAndStatusInOrderByCreatedAtDesc(internalFundingId,
                        List.of(PaymentStatus.COMPLETED.name(), PaymentStatus.CANCELLED.name()))
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findPendingByFundingId(UUID fundingId) {
        return jpaRepository.findFirstByFundingOrderIdAndPurposeAndStatusOrderByCreatedAtDesc(fundingId,
                        PaymentPurpose.REWARD.name(), PaymentStatus.PENDING.name())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findLatestExchangeFeeByRefundRequestId(Long refundRequestId) {
        return jpaRepository.findFirstByRefundRequestIdOrderByCreatedAtDesc(refundRequestId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByPgOrderId(String pgOrderId) {
        return jpaRepository.existsByPgOrderId(pgOrderId);
    }
}
