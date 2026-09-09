package com.fundit.payment.infrastructure.persistence.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementHoldJpaRepository extends JpaRepository<SettlementHoldJpaEntity, Long> {

    Optional<SettlementHoldJpaEntity> findByPaymentId(UUID paymentId);
}
