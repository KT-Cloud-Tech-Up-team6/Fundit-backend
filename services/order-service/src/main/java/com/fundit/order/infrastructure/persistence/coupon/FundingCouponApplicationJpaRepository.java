package com.fundit.order.infrastructure.persistence.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundingCouponApplicationJpaRepository extends JpaRepository<FundingCouponApplicationJpaEntity, Long> {

    List<FundingCouponApplicationJpaEntity> findByFundingId(Long fundingId);
}
