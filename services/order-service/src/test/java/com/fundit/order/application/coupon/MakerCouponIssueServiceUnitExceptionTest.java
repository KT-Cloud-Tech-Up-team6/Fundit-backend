package com.fundit.order.application.coupon;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.DropType;
import com.fundit.order.domain.coupon.IssueChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakerCouponIssueServiceUnitExceptionTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;

    @InjectMocks
    private MakerCouponIssueService makerCouponIssueService;

    private MakerCouponIssueCommand command(DiscountType discountType, long discountValue, Long maxDiscountAmount,
                                             Long budgetLimit, int quantity) {
        return new MakerCouponIssueCommand(PROJECT_ID, "쿠폰", discountType, discountValue, maxDiscountAmount,
                budgetLimit, quantity, 0L, 1, Instant.now().plus(30, ChronoUnit.DAYS),
                IssueChannel.GENERAL, null, null, null);
    }

    @Test
    void 존재하지_않는_프로젝트면_NOT_FOUND_예외가_발생한다() {
        // given
        when(projectOwnershipClient.findSellerId(PROJECT_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> makerCouponIssueService.issue(UUID.randomUUID(),
                command(DiscountType.AMOUNT, 1000, null, null, 10)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 본인_소유가_아니면_FORBIDDEN_예외가_발생한다() {
        // given
        when(projectOwnershipClient.findSellerId(PROJECT_ID)).thenReturn(Optional.of(UUID.randomUUID()));

        // when & then
        assertThatThrownBy(() -> makerCouponIssueService.issue(UUID.randomUUID(),
                command(DiscountType.AMOUNT, 1000, null, null, 10)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 발급수량_전량_사용시_예산을_명백히_초과하면_COUPON_BUDGET_EXCEEDED_예외가_발생한다() {
        // given — 1,000원 x 200개 = 200,000 > 예산한도 100,000
        UUID sellerId = UUID.randomUUID();
        when(projectOwnershipClient.findSellerId(PROJECT_ID)).thenReturn(Optional.of(sellerId));

        // when & then
        assertThatThrownBy(() -> makerCouponIssueService.issue(sellerId,
                command(DiscountType.AMOUNT, 1_000, null, 100_000L, 200)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(OrderErrorCode.COUPON_BUDGET_EXCEEDED));
    }

    @Test
    void 남의_방송에_LIVE_쿠폰을_매달면_FORBIDDEN_예외가_발생한다() {
        // given — 프로젝트는 내 것이지만 방송은 남의 것. 통과시키면 남의 시청자에게 내 쿠폰이 뿌려진다.
        UUID sellerId = UUID.randomUUID();
        MakerCouponIssueCommand liveCommand = new MakerCouponIssueCommand(PROJECT_ID, "라이브 특가",
                DiscountType.AMOUNT, 3_000L, null, null, 100, 0L, 1,
                Instant.now().plus(30, ChronoUnit.DAYS),
                IssueChannel.LIVE, DropType.FIRST_COME, 42L, UUID.randomUUID());
        when(projectOwnershipClient.findSellerId(PROJECT_ID)).thenReturn(Optional.of(sellerId));

        // when & then
        assertThatThrownBy(() -> makerCouponIssueService.issue(sellerId, liveCommand))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }
}
