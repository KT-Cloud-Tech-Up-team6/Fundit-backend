package com.fundit.order.infrastructure.persistence.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponIssuanceJpaRepository extends JpaRepository<CouponIssuanceJpaEntity, Long> {

    Optional<CouponIssuanceJpaEntity> findByCouponCodeAndOwnerId(String couponCode, UUID ownerId);

    Page<CouponIssuanceJpaEntity> findByOwnerIdAndStatus(UUID ownerId, String status, Pageable pageable);

    Page<CouponIssuanceJpaEntity> findByOwnerId(UUID ownerId, Pageable pageable);

    long countByCouponCodeAndOwnerId(String couponCode, UUID ownerId);

    /**
     * coupon_issuances와 coupons는 coupon_code로만 연결돼 있고 JPA 연관관계 매핑이 없어(각자
     * 독립 애그리거트) 네이티브 쿼리로 조인한다 — 쿠폰 만료 배치(신규) 대상 조회 전용.
     */
    @Query(value = "SELECT ci.* FROM coupon_issuances ci "
            + "JOIN coupons c ON c.coupon_code = ci.coupon_code "
            + "WHERE ci.status = 'AVAILABLE' AND c.expires_at <= :now "
            + "ORDER BY ci.id ASC", nativeQuery = true)
    List<CouponIssuanceJpaEntity> findAvailableExpired(@Param("now") Instant now);

    /** 쿠폰 만료임박(COUPON_EXPIRING) 리마인더 배치 대상 조회 — 아직 리마인더를 보내지 않은 건만. */
    @Query(value = "SELECT ci.* FROM coupon_issuances ci "
            + "JOIN coupons c ON c.coupon_code = ci.coupon_code "
            + "WHERE ci.status = 'AVAILABLE' AND ci.expiring_notified_at IS NULL "
            + "AND c.expires_at BETWEEN :now AND :windowEnd "
            + "ORDER BY ci.id ASC", nativeQuery = true)
    List<CouponIssuanceJpaEntity> findAvailableExpiringWithin(@Param("now") Instant now, @Param("windowEnd") Instant windowEnd);
}
