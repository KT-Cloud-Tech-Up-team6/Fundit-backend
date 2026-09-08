package com.fundit.order.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CouponIssuanceRepository {

    Optional<CouponIssuance> findByCouponCodeAndOwnerId(String couponCode, UUID ownerId);

    Optional<CouponIssuance> findById(Long id);

    Page<CouponIssuance> findByOwnerId(UUID ownerId, CouponIssuanceStatus status, Pageable pageable);

    /** ORDER-012 per_member_limit 검증용 — 같은 회원이 같은 쿠폰코드를 몇 번 발급받았는지. */
    long countByCouponCodeAndOwnerId(String couponCode, UUID ownerId);

    CouponIssuance save(CouponIssuance issuance);
}
