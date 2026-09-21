package com.fundit.order.infrastructure.persistence.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FundingCouponApplicationJpaRepository extends JpaRepository<FundingCouponApplicationJpaEntity, Long> {

    List<FundingCouponApplicationJpaEntity> findByFundingId(Long fundingId);

    /** ORDER-004 목록 화면 배치 조회용 — 건별 findByFundingId 반복(N+1) 방지. */
    @Query("""
            SELECT f.fundingId AS fundingId, COALESCE(SUM(f.discountAmount), 0) AS totalDiscount
            FROM FundingCouponApplicationJpaEntity f
            WHERE f.fundingId IN :fundingIds
            GROUP BY f.fundingId
            """)
    List<FundingDiscountProjection> sumDiscountAmountByFundingIdIn(@Param("fundingIds") List<Long> fundingIds);

    interface FundingDiscountProjection {
        Long getFundingId();
        Long getTotalDiscount();
    }

    /**
     * PAYMENT-012 정산 집계용 — 메이커 발급 쿠폰(issuer_type='MAKER')만 차감 대상이다.
     * 플랫폼 발급 쿠폰은 플랫폼이 부담하므로 이 합계에 포함하지 않는다(PRD 16.5.3).
     * coupon_issuances/coupons에 도메인 엔티티 연관관계가 없어(단순 애그리거트) 네이티브 조인으로 조회한다.
     */
    @Query(value = """
            SELECT COALESCE(SUM(fca.discount_amount), 0)
            FROM funding_coupon_applications fca
            JOIN coupon_issuances ci ON ci.id = fca.coupon_issuance_id
            JOIN coupons c ON c.coupon_code = ci.coupon_code
            WHERE fca.funding_id = :fundingId AND c.issuer_type = 'MAKER'
            """, nativeQuery = true)
    long sumMakerCouponDiscountAmount(@Param("fundingId") Long fundingId);
}
