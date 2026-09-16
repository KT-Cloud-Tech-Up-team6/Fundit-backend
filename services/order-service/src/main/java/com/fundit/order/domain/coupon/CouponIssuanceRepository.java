package com.fundit.order.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponIssuanceRepository {

    Optional<CouponIssuance> findByCouponCodeAndOwnerId(String couponCode, UUID ownerId);

    Optional<CouponIssuance> findById(Long id);

    Page<CouponIssuance> findByOwnerId(UUID ownerId, CouponIssuanceStatus status, Pageable pageable);

    /** ORDER-012 per_member_limit 검증용 — 같은 회원이 같은 쿠폰코드를 몇 번 발급받았는지. */
    long countByCouponCodeAndOwnerId(String couponCode, UUID ownerId);

    /** 쿠폰 만료 배치 대상 조회 — AVAILABLE인데 연결된 쿠폰 템플릿의 유효기간이 지난 발급 건. */
    List<CouponIssuance> findAvailableExpired(Instant now);

    /** 쿠폰 만료임박 리마인더 배치 대상 조회 — AVAILABLE, 유효기간이 [now, windowEnd] 사이, 아직 리마인더 미발송. */
    List<CouponIssuance> findAvailableExpiringWithin(Instant now, Instant windowEnd);

    CouponIssuance save(CouponIssuance issuance);
}
