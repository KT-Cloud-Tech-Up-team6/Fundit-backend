package com.fundit.order.application.order;

import com.fundit.order.application.catalog.ProjectSummaryClient;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssuerType;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.domain.inventory.InventoryRepository;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaEntity;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreateServiceUnitTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Long PROJECT_ID = 10L;
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

    private OrderPricingService.PricingResult pricingResultWithCoupon() {
        var lineItem = new OrderPricingService.ResolvedLineItem(REWARD_ID, "리워드", 2, 10_000L, List.of());
        var applied = new OrderPricingService.AppliedCoupon(5L, "WELCOME", IssuerType.PLATFORM, DiscountType.AMOUNT, 2_000L);
        return new OrderPricingService.PricingResult(20_000L, 3_000L, 2_000L, 21_000L,
                List.of(lineItem), List.of(applied), List.of());
    }

    @Test
    void 재고를_차감하고_주문을_생성한다() {
        // given
        OrderPricingService.PricingResult pricing = pricingResultWithCoupon();
        when(orderPricingService.calculate(eq(MEMBER_ID), eq(PROJECT_ID), any(), any())).thenReturn(pricing);
        when(inventoryRepository.decreaseStock(REWARD_ID, 2)).thenReturn(true);
        when(projectSummaryClient.getProjectTitle(PROJECT_ID)).thenReturn(Optional.of("프로젝트"));
        Funding savedFunding = Funding.builder().id(100L).publicId(UUID.randomUUID()).memberId(MEMBER_ID)
                .projectId(PROJECT_ID).projectTitle("프로젝트")
                .status(com.fundit.order.domain.funding.FundingStatus.PENDING)
                .shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(3_000L).paymentExpiresAt(Instant.now().plusSeconds(1800))
                .lineItems(List.of()).createdAt(Instant.now()).build();
        when(fundingRepository.save(any())).thenReturn(savedFunding);

        // when
        OrderCreateService.OrderCreateResult result = orderCreateService.create(MEMBER_ID, PROJECT_ID,
                List.of(new OrderLineItemRequest(REWARD_ID, 2, null)),
                new ShippingAddress("홍길동", "010", "12345", "주소", null), List.of("WELCOME"));

        // then
        assertThat(result.funding()).isEqualTo(savedFunding);
        assertThat(result.finalAmount()).isEqualTo(21_000L);
        verify(inventoryRepository).decreaseStock(REWARD_ID, 2);

        ArgumentCaptor<FundingCouponApplicationJpaEntity> captor =
                ArgumentCaptor.forClass(FundingCouponApplicationJpaEntity.class);
        verify(couponApplicationJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getFundingId()).isEqualTo(100L);
        assertThat(captor.getValue().getCouponIssuanceId()).isEqualTo(5L);
        assertThat(captor.getValue().getDiscountAmount()).isEqualTo(2_000L);
        verify(couponRepository).increaseUsedBudget("WELCOME", 2_000L);
    }

    @Test
    void 프로젝트_제목_조회에_실패해도_빈문자열로_주문이_생성된다() {
        // given
        var lineItem = new OrderPricingService.ResolvedLineItem(REWARD_ID, "리워드", 1, 10_000L, List.of());
        OrderPricingService.PricingResult pricing = new OrderPricingService.PricingResult(
                10_000L, 3_000L, 0L, 13_000L, List.of(lineItem), List.of(), List.of());
        when(orderPricingService.calculate(eq(MEMBER_ID), eq(PROJECT_ID), any(), any())).thenReturn(pricing);
        when(inventoryRepository.decreaseStock(REWARD_ID, 1)).thenReturn(true);
        when(projectSummaryClient.getProjectTitle(PROJECT_ID)).thenReturn(Optional.empty());
        when(fundingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        orderCreateService.create(MEMBER_ID, PROJECT_ID, List.of(new OrderLineItemRequest(REWARD_ID, 1, null)),
                new ShippingAddress("홍길동", "010", "12345", "주소", null), null);

        // then
        ArgumentCaptor<Funding> captor = ArgumentCaptor.forClass(Funding.class);
        verify(fundingRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectTitle()).isEmpty();
    }
}
