package com.fundit.payment.infrastructure.persistence.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementBatchJpaRepository extends JpaRepository<SettlementBatchJpaEntity, Long> {

    List<SettlementBatchJpaEntity> findByStatus(String status);
}
