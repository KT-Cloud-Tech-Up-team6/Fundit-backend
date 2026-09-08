package com.fundit.order.infrastructure.persistence.funding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundingLineItemJpaRepository extends JpaRepository<FundingLineItemJpaEntity, Long> {

    List<FundingLineItemJpaEntity> findByFundingId(Long fundingId);
}
