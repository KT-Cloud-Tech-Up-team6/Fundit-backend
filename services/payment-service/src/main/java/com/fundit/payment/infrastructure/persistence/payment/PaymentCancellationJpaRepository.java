package com.fundit.payment.infrastructure.persistence.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentCancellationJpaRepository extends JpaRepository<PaymentCancellationJpaEntity, Long> {

    List<PaymentCancellationJpaEntity> findByPaymentId(UUID paymentId);
}
