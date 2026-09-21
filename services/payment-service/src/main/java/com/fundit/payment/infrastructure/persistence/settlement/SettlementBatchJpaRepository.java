package com.fundit.payment.infrastructure.persistence.settlement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SettlementBatchJpaRepository extends JpaRepository<SettlementBatchJpaEntity, Long> {

    List<SettlementBatchJpaEntity> findByStatus(String status);

    /** 판매자 정산 목록 — settlementBatchId를 확인해 상세(PAYMENT-009) 조회로 이어가는 진입점. */
    Page<SettlementBatchJpaEntity> findBySellerIdOrderByIdDesc(UUID sellerId, Pageable pageable);
}
