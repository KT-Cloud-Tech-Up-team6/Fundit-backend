package com.fundit.order.infrastructure.persistence.funding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundingLineItemOptionJpaRepository extends JpaRepository<FundingLineItemOptionJpaEntity, Long> {

    List<FundingLineItemOptionJpaEntity> findByFundingLineItemIdIn(List<Long> fundingLineItemIds);
}
