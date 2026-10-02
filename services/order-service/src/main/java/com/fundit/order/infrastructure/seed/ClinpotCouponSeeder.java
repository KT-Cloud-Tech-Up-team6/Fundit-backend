package com.fundit.order.infrastructure.seed;

import com.fundit.order.domain.coupon.CouponIssuanceStatus;
import com.fundit.order.domain.coupon.CouponTargetScope;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssueChannel;
import com.fundit.order.domain.coupon.IssuerType;
import com.fundit.order.infrastructure.persistence.coupon.CouponIssuanceJpaEntity;
import com.fundit.order.infrastructure.persistence.coupon.CouponIssuanceJpaRepository;
import com.fundit.order.infrastructure.persistence.coupon.CouponJpaEntity;
import com.fundit.order.infrastructure.persistence.coupon.CouponJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * dev 전용 시연 데이터(#234) — 소비자 시연용 클린팟 쿠폰 1종을 만들고 시연 계정 쿠폰함에 미리 넣어 둔다.
 * PM 스펙: "라이브 특별 쿠폰", 10% 할인, 5,000원 이상 사용 가능. 최대 할인액은 정하지 않아 상한이 없다.
 *
 * <p>시연 계정은 클린팟 판매자 계정이다 — PM이 판매자·소비자 시연을 한 계정으로 진행한다(order에 역할 검사나
 * "내 프로젝트 구매" 차단이 없다). 그래서 발급자와 수령자가 같다.
 *
 * <p>채널은 {@code GENERAL}이다 — {@code LIVE}는 {@code live_session_id}가 있어야 하는데 연결할 방송이 아직 없다.
 * LIVE 판정은 받기({@code CouponClaimService})에서만 하고 결제 시엔 보지 않으므로, 미리 발급해 둔 쿠폰은 채널과
 * 무관하게 쓸 수 있다.
 *
 * <p>쿠폰 코드로 멱등 처리한다 — 이미 있으면 건너뛴다. 실패해도 기동은 막지 않는다.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class ClinpotCouponSeeder implements ApplicationRunner {

    static final String COUPON_CODE = "LIVECLINPOT";
    static final String COUPON_NAME = "라이브 특별 쿠폰";
    /** 클린팟 publicId — {@link ClinpotFundingSeeder#PROJECT_ID}와 같다. */
    static final UUID PROJECT_ID = ClinpotFundingSeeder.PROJECT_ID;
    /** 클린팟 판매자 = 시연 계정(PM 확인, 실제 가입 계정). */
    static final UUID DEMO_MEMBER_ID = UUID.fromString("01a0f9ff-2d11-722d-b68e-6b074bfb108a");
    static final int QUANTITY = 100;
    /** KST 2026-12-31 23:59:59 — 만료 배치에 걸리지 않게 넉넉히. */
    static final Instant EXPIRES_AT = Instant.parse("2026-12-31T14:59:59Z");

    private final CouponJpaRepository couponRepository;
    private final CouponIssuanceJpaRepository issuanceRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException e) {
            log.warn("클린팟 쿠폰 시드 실패", e);
        }
    }

    void seed() {
        if (couponRepository.findByCouponCode(COUPON_CODE).isPresent()) {
            return;
        }
        // 쿠폰과 발급을 한 트랜잭션으로 — 나뉘면 다음 기동에 "이미 있음"으로 건너뛰어 발급이 영영 안 채워진다.
        transactionTemplate.executeWithoutResult(status -> {
            couponRepository.save(CouponJpaEntity.builder()
                    .couponCode(COUPON_CODE)
                    .couponName(COUPON_NAME)
                    .discountType(DiscountType.RATE.name())
                    .discountValue(10)
                    .usedBudgetAmount(0)
                    .issuerType(IssuerType.MAKER.name())
                    .issuerId(DEMO_MEMBER_ID)
                    .targetScope(CouponTargetScope.PROJECT.name())
                    .targetRefId(String.valueOf(PROJECT_ID))
                    .minFundingAmount(5_000)
                    .perMemberLimit(1)
                    .remainingQuantity(QUANTITY - 1)
                    .expiresAt(EXPIRES_AT)
                    .issueChannel(IssueChannel.GENERAL.name())
                    .version(0)
                    .build());
            issuanceRepository.save(CouponIssuanceJpaEntity.builder()
                    .couponCode(COUPON_CODE)
                    .ownerId(DEMO_MEMBER_ID)
                    .status(CouponIssuanceStatus.AVAILABLE.name())
                    .build());
        });
        log.info("클린팟 쿠폰 시드 완료 code={}", COUPON_CODE);
    }
}
