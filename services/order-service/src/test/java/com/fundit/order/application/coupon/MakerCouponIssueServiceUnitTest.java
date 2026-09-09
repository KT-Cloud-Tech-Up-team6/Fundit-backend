package com.fundit.order.application.coupon;

import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.DiscountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakerCouponIssueServiceUnitTest {

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;

    @InjectMocks
    private MakerCouponIssueService makerCouponIssueService;

    private MakerCouponIssueCommand command(DiscountType discountType, long discountValue, Long maxDiscountAmount,
                                             Long budgetLimit, int quantity) {
        return new MakerCouponIssueCommand(123L, "오픈 기념 할인", discountType, discountValue, maxDiscountAmount,
                budgetLimit, quantity, 30_000L, 1, Instant.now().plus(30, ChronoUnit.DAYS));
    }

    @Test
    void 본인_소유_프로젝트면_쿠폰이_발급된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        when(projectOwnershipClient.findSellerId(123L)).thenReturn(Optional.of(sellerId));
        when(couponRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        Coupon coupon = makerCouponIssueService.issue(sellerId, command(DiscountType.RATE, 10, 5_000L, 1_000_000L, 200));

        // then
        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getIssuerId()).isEqualTo(sellerId);
        assertThat(captor.getValue().getRemainingQuantity()).isEqualTo(200);
        assertThat(coupon.getCouponCode()).startsWith("P123-");
    }

    @Test
    void FREE_SHIPPING이면_discountValue가_0으로_저장된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        when(projectOwnershipClient.findSellerId(123L)).thenReturn(Optional.of(sellerId));
        when(couponRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        Coupon coupon = makerCouponIssueService.issue(sellerId,
                command(DiscountType.FREE_SHIPPING, 9999, null, null, 100));

        // then
        assertThat(coupon.getDiscountValue()).isZero();
    }
}
