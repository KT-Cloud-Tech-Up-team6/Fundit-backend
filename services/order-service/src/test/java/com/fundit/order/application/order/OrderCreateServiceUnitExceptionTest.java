package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.order.application.catalog.ProjectSummaryClient;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.domain.inventory.InventoryRepository;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreateServiceUnitExceptionTest {

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

    @Test
    void 재고가_부족하면_INSUFFICIENT_STOCK_예외가_발생하고_주문이_저장되지_않는다() {
        // given
        var lineItem = new OrderPricingService.ResolvedLineItem(REWARD_ID, "리워드", 5, 10_000L, List.of());
        OrderPricingService.PricingResult pricing = new OrderPricingService.PricingResult(
                50_000L, 3_000L, 0L, 53_000L, List.of(lineItem), List.of(), List.of());
        when(orderPricingService.calculate(eq(MEMBER_ID), eq(PROJECT_ID), any(), any())).thenReturn(pricing);
        when(inventoryRepository.decreaseStock(REWARD_ID, 5)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> orderCreateService.create(MEMBER_ID, PROJECT_ID,
                List.of(new OrderLineItemRequest(REWARD_ID, 5, null)),
                new ShippingAddress("홍길동", "010", "12345", "주소", null), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(OrderErrorCode.INSUFFICIENT_STOCK));

        verify(fundingRepository, never()).save(any());
    }
}
