package com.fundit.order.infrastructure.persistence.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponIssuanceJpaRepository extends JpaRepository<CouponIssuanceJpaEntity, Long> {

    Optional<CouponIssuanceJpaEntity> findByCouponCodeAndOwnerId(String couponCode, UUID ownerId);

    Page<CouponIssuanceJpaEntity> findByOwnerIdAndStatus(UUID ownerId, String status, Pageable pageable);

    Page<CouponIssuanceJpaEntity> findByOwnerId(UUID ownerId, Pageable pageable);

    long countByCouponCodeAndOwnerId(String couponCode, UUID ownerId);
}
