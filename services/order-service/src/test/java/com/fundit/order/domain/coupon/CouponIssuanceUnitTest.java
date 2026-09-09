package com.fundit.order.domain.coupon;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CouponIssuanceUnitTest {

    @Test
    void 발급하면_AVAILABLE_상태다() {
        // when
        CouponIssuance issuance = CouponIssuance.issue("CODE1", UUID.randomUUID());

        // then
        assertThat(issuance.getStatus()).isEqualTo(CouponIssuanceStatus.AVAILABLE);
        assertThat(issuance.isAvailable()).isTrue();
    }

    @Test
    void 본인_소유_여부를_확인할_수_있다() {
        // given
        UUID owner = UUID.randomUUID();
        CouponIssuance issuance = CouponIssuance.issue("CODE1", owner);

        // when & then
        assertThat(issuance.isOwnedBy(owner)).isTrue();
        assertThat(issuance.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void 사용처리하면_USED_상태가_되고_사용_주문이_기록된다() {
        // given
        CouponIssuance issuance = CouponIssuance.issue("CODE1", UUID.randomUUID());

        // when
        issuance.markUsed(100L);

        // then
        assertThat(issuance.getStatus()).isEqualTo(CouponIssuanceStatus.USED);
        assertThat(issuance.getUsedFundingId()).isEqualTo(100L);
        assertThat(issuance.getUsedAt()).isNotNull();
        assertThat(issuance.isAvailable()).isFalse();
    }

    @Test
    void 복원하면_다시_AVAILABLE_상태가_되고_사용이력이_지워진다() {
        // given
        CouponIssuance issuance = CouponIssuance.issue("CODE1", UUID.randomUUID());
        issuance.markUsed(100L);

        // when
        issuance.restore();

        // then
        assertThat(issuance.getStatus()).isEqualTo(CouponIssuanceStatus.AVAILABLE);
        assertThat(issuance.getUsedFundingId()).isNull();
        assertThat(issuance.getRestoredAt()).isNotNull();
    }
}
