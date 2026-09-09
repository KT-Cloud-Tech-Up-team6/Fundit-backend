package com.fundit.payment.infrastructure.persistence.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementDisputeJpaRepository extends JpaRepository<SettlementDisputeJpaEntity, Long> {
}
