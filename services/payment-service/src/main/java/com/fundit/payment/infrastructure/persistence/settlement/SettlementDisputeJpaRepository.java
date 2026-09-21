package com.fundit.payment.infrastructure.persistence.settlement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettlementDisputeJpaRepository extends JpaRepository<SettlementDisputeJpaEntity, Long> {

    /** 판매자 정산 이의신청 목록/상태 조회. */
    Page<SettlementDisputeJpaEntity> findBySellerIdOrderByIdDesc(UUID sellerId, Pageable pageable);
}
