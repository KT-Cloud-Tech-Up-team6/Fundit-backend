package com.fundit.order.application.coupon;

import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.CouponTargetScope;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssueChannel;
import com.fundit.order.domain.coupon.IssuerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponBoxQueryServiceUnitTest {

    @Mock
    private CouponIssuanceRepository couponIssuanceRepository;
    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponBoxQueryService couponBoxQueryService;

    @Test
    void 발급목록과_쿠폰템플릿을_조합해서_반환한다() {
        // given
        UUID memberId = UUID.randomUUID();
        CouponIssuance issuance = CouponIssuance.issue("WELCOME2026", memberId);
        Coupon coupon = Coupon.builder().couponCode("WELCOME2026").couponName("신규가입 축하 쿠폰")
                .discountType(DiscountType.AMOUNT).discountValue(3_000)
                .issuerType(IssuerType.PLATFORM).targetScope(CouponTargetScope.ALL)
                .minFundingAmount(0).perMemberLimit(1).remainingQuantity(10)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).issueChannel(IssueChannel.GENERAL).version(0).build();

        when(couponIssuanceRepository.findByOwnerId(any(), any(), any())).thenReturn(new PageImpl<>(List.of(issuance)));
        when(couponRepository.findByCouponCodeIn(List.of("WELCOME2026"))).thenReturn(List.of(coupon));

        // when
        var result = couponBoxQueryService.list(memberId, null, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.coupon().getCouponName()).isEqualTo("신규가입 축하 쿠폰");
            assertThat(item.issuance().getCouponCode()).isEqualTo("WELCOME2026");
        });
    }
}
