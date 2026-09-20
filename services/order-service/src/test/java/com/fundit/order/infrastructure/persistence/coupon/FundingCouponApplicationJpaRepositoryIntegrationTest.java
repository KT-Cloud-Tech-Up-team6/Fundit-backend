package com.fundit.order.infrastructure.persistence.coupon;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.ShippingAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAYMENT-012 정산 집계용 네이티브 조인(coupon_issuances/coupons)이 issuer_type='MAKER'만
 * 정확히 걸러내는지 실제 DB에서 검증한다 — 파생/네이티브 쿼리는 이름·조인 조건을 잘못 지어도
 * 컴파일은 통과하고 조용히 다른 결과를 내놓기 때문이다(event-convention.md 토픽명 오탈자와 같은 부류의 위험).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class FundingCouponApplicationJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FundingRepository fundingRepository;
    @Autowired
    private CouponJpaRepository couponJpaRepository;
    @Autowired
    private CouponIssuanceJpaRepository couponIssuanceJpaRepository;
    @Autowired
    private FundingCouponApplicationJpaRepository couponApplicationJpaRepository;

    private void seedCoupon(String code, String issuerType) {
        couponJpaRepository.save(CouponJpaEntity.builder()
                .couponCode(code).couponName("쿠폰")
                .discountType("AMOUNT").discountValue(1_000)
                .issuerType(issuerType).targetScope("ALL")
                .minFundingAmount(0).perMemberLimit(1).remainingQuantity(10)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).issueChannel("GENERAL")
                .version(0).build());
    }

    private Long seedIssuance(String code, UUID ownerId) {
        return couponIssuanceJpaRepository.save(CouponIssuanceJpaEntity.builder()
                .couponCode(code).ownerId(ownerId).status("USED").build()).getId();
    }

    private Long seedFunding() {
        Funding funding = Funding.create(UUID.randomUUID(), UUID.randomUUID(), "테스트 프로젝트",
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 어딘가", null),
                0L, List.of(), Instant.now().plusSeconds(3600));
        return fundingRepository.save(funding).getId();
    }

    @Test
    void 메이커_쿠폰_할인액만_합산하고_플랫폼_쿠폰은_제외한다() {
        // given
        UUID ownerId = UUID.randomUUID();
        seedCoupon("PLAT1", "PLATFORM");
        seedCoupon("MAKE1", "MAKER");
        Long platformIssuanceId = seedIssuance("PLAT1", ownerId);
        Long makerIssuanceId = seedIssuance("MAKE1", ownerId);
        Long fundingId = seedFunding();

        couponApplicationJpaRepository.save(FundingCouponApplicationJpaEntity.builder()
                .fundingId(fundingId).couponIssuanceId(platformIssuanceId).discountAmount(1_000L).build());
        couponApplicationJpaRepository.save(FundingCouponApplicationJpaEntity.builder()
                .fundingId(fundingId).couponIssuanceId(makerIssuanceId).discountAmount(2_000L).build());

        // when
        long result = couponApplicationJpaRepository.sumMakerCouponDiscountAmount(fundingId);

        // then — 플랫폼 쿠폰(1,000원)은 제외하고 메이커 쿠폰(2,000원)만 합산된다
        assertThat(result).isEqualTo(2_000L);
    }

    @Test
    void 적용된_쿠폰이_없으면_0을_반환한다() {
        // given
        Long fundingId = seedFunding();

        // when
        long result = couponApplicationJpaRepository.sumMakerCouponDiscountAmount(fundingId);

        // then
        assertThat(result).isZero();
    }
}
