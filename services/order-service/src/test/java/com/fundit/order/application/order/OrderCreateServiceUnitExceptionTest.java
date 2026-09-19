package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.order.application.catalog.ProjectSummaryClient;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssuerType;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.domain.inventory.InventoryRepository;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreateServiceUnitExceptionTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Long REWARD_ID = 1L;

    @Mock
    private OrderPricingService orderPricingService;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private FundingRepository fundingRepository;
    @Mock
    private FundingCouponApplicationJpaRepository couponApplicationJpaRepository;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private ProjectSummaryClient projectSummaryClient;

    private OrderCreateService orderCreateService;

    @BeforeEach
    void setUp() {
        orderCreateService = new OrderCreateService(orderPricingService, inventoryRepository, fundingRepository,
                couponApplicationJpaRepository, couponRepository, projectSummaryClient, 30L);
    }

    @Test
    void 재고가_부족하면_INSUFFICIENT_STOCK_예외가_발생하고_주문이_저장되지_않는다() {
        // given
        var lineItem = new OrderPricingService.ResolvedLineItem(REWARD_ID, "리워드", 5, 10_000L, List.of());
        OrderPricingService.PricingResult pricing = new OrderPricingService.PricingResult(
                50_000L, 3_000L, 0L, 53_000L, List.of(lineItem), List.of(), List.of());
        when(orderPricingService.calculate(eq(MEMBER_ID), eq(PROJECT_ID), any(), any(), anyBoolean())).thenReturn(pricing);
        when(inventoryRepository.decreaseStock(REWARD_ID, 5)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> orderCreateService.create(MEMBER_ID, PROJECT_ID,
                List.of(new OrderLineItemRequest(REWARD_ID, 5, null)),
                new ShippingAddress("홍길동", "010", "12345", "주소", null), null, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(OrderErrorCode.INSUFFICIENT_STOCK));

        verify(fundingRepository, never()).save(any());
    }

    @Test
    void 쿠폰_예산_반영에_실패하면_COUPON_BUDGET_EXCEEDED_예외가_발생한다() {
        // given — resolveSingleCoupon()이 조회 시점엔 예산이 남아있다고 판단했지만, 그 사이 다른
        // 요청이 예산을 먼저 소진시켜(레이스) increaseUsedBudget()의 조건부 UPDATE가 0건 반영된 경우.
        var lineItem = new OrderPricingService.ResolvedLineItem(REWARD_ID, "리워드", 1, 10_000L, List.of());
        var applied = new OrderPricingService.AppliedCoupon(5L, "RACE", IssuerType.MAKER, DiscountType.AMOUNT, 2_000L);
        OrderPricingService.PricingResult pricing = new OrderPricingService.PricingResult(
                10_000L, 3_000L, 2_000L, 11_000L, List.of(lineItem), List.of(applied), List.of());
        when(orderPricingService.calculate(eq(MEMBER_ID), eq(PROJECT_ID), any(), any(), anyBoolean())).thenReturn(pricing);
        when(inventoryRepository.decreaseStock(REWARD_ID, 1)).thenReturn(true);
        when(projectSummaryClient.getProjectTitle(PROJECT_ID)).thenReturn(Optional.of("프로젝트"));
        when(fundingRepository.save(any())).thenReturn(Funding.builder().id(100L).publicId(UUID.randomUUID())
                .memberId(MEMBER_ID).projectId(PROJECT_ID).projectTitle("프로젝트")
                .status(com.fundit.order.domain.funding.FundingStatus.PENDING)
                .shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(3_000L).paymentExpiresAt(Instant.now().plusSeconds(1800))
                .lineItems(List.of()).createdAt(Instant.now()).build());
        when(couponRepository.increaseUsedBudget("RACE", 2_000L)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> orderCreateService.create(MEMBER_ID, PROJECT_ID,
                List.of(new OrderLineItemRequest(REWARD_ID, 1, null)),
                new ShippingAddress("홍길동", "010", "12345", "주소", null), List.of("RACE"), false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(OrderErrorCode.COUPON_BUDGET_EXCEEDED));
    }
}
