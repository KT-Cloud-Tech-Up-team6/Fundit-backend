package com.fundit.order.infrastructure.seed;

import com.fundit.order.infrastructure.persistence.coupon.CouponIssuanceJpaEntity;
import com.fundit.order.infrastructure.persistence.coupon.CouponIssuanceJpaRepository;
import com.fundit.order.infrastructure.persistence.coupon.CouponJpaEntity;
import com.fundit.order.infrastructure.persistence.coupon.CouponJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ClinpotCouponSeederUnitTest {

    @Mock private CouponJpaRepository couponRepository;
    @Mock private CouponIssuanceJpaRepository issuanceRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private ClinpotCouponSeeder seeder() {
        return new ClinpotCouponSeeder(couponRepository, issuanceRepository, transactionTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void 쿠폰_코드가_없으면_클린팟_전용_10퍼센트_쿠폰을_만들고_시연_계정에_발급한다() {
        // given
        given(couponRepository.findByCouponCode(ClinpotCouponSeeder.COUPON_CODE)).willReturn(Optional.empty());
        willAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).given(transactionTemplate).executeWithoutResult(any());

        // when
        seeder().seed();

        // then
        ArgumentCaptor<CouponJpaEntity> coupon = ArgumentCaptor.forClass(CouponJpaEntity.class);
        verify(couponRepository).save(coupon.capture());
        CouponJpaEntity saved = coupon.getValue();
        assertThat(saved.getCouponName()).isEqualTo("라이브 특별 쿠폰");
        assertThat(saved.getDiscountType()).isEqualTo("RATE");
        assertThat(saved.getDiscountValue()).isEqualTo(10);
        assertThat(saved.getMinFundingAmount()).isEqualTo(5_000);
        // Coupon.matchesProject가 String.valueOf(projectId)와 비교한다 — 형식이 다르면 적용 불가로 나온다
        assertThat(saved.getTargetScope()).isEqualTo("PROJECT");
        assertThat(saved.getTargetRefId()).isEqualTo("01a0fa1e-074a-7d87-a43b-4e0a947bfd13");
        assertThat(saved.getRemainingQuantity()).isEqualTo(99);

        ArgumentCaptor<CouponIssuanceJpaEntity> issuance = ArgumentCaptor.forClass(CouponIssuanceJpaEntity.class);
        verify(issuanceRepository).save(issuance.capture());
        assertThat(issuance.getValue().getOwnerId()).isEqualTo(ClinpotCouponSeeder.DEMO_MEMBER_ID);
        assertThat(issuance.getValue().getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void 이미_있으면_아무것도_하지_않는다() {
        // given
        given(couponRepository.findByCouponCode(ClinpotCouponSeeder.COUPON_CODE))
                .willReturn(Optional.of(CouponJpaEntity.builder().build()));

        // when
        seeder().seed();

        // then
        verifyNoInteractions(transactionTemplate, issuanceRepository);
    }
}
