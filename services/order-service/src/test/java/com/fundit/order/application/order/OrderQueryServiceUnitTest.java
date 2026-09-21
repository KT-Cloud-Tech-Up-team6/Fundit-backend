package com.fundit.order.application.order;

import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.application.catalog.ProjectSummaryClient;
import com.fundit.order.application.fulfillment.FulfillmentStatusClient;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaEntity;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceUnitTest {

    @Mock
    private FundingRepository fundingRepository;
    @Mock
    private FundingCouponApplicationJpaRepository couponApplicationJpaRepository;
    @Mock
    private FulfillmentStatusClient fulfillmentStatusClient;
    @Mock
    private ProjectSummaryClient projectSummaryClient;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Test
    void 판매자_발송목록은_소유권_검증후_성립건만_반환한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(UUID.randomUUID()).memberId(UUID.randomUUID())
                .projectId(projectId).status(FundingStatus.GOAL_ACHIEVED)
                .shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now())
                .lineItems(List.of(new FundingLineItem(1L, 5L, "리워드", 1, 10_000L, List.of())))
                .createdAt(Instant.now()).build();
        when(projectOwnershipClient.findSellerId(projectId)).thenReturn(Optional.of(sellerId));
        when(fundingRepository.findGoalAchievedByProjectId(projectId)).thenReturn(List.of(funding));

        // when
        var result = orderQueryService.listForSeller(sellerId, projectId);

        // then
        assertThat(result).containsExactly(funding);
    }

    @Test
    void 목록조회는_리포지토리에_위임한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(fundingRepository.findByMemberId(any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(projectSummaryClient.getSummaries(any())).thenReturn(java.util.Map.of());
        when(fulfillmentStatusClient.fetchBatch(any())).thenReturn(java.util.Map.of());

        // when
        var result = orderQueryService.listMyOrders(memberId, FundingStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 20));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 목록조회_결과에_창작자명_썸네일_가능액션이_채워진다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(UUID.randomUUID()).memberId(memberId).projectId(projectId)
                .status(FundingStatus.PENDING).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now().plusSeconds(1800))
                .lineItems(List.of(new FundingLineItem(1L, 5L, "리워드", 2, 10_000L, List.of())))
                .createdAt(Instant.now()).build();
        when(fundingRepository.findByMemberId(any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(funding)));
        when(projectSummaryClient.getSummaries(List.of(projectId))).thenReturn(java.util.Map.of(projectId,
                new ProjectSummaryClient.ProjectSummary("프로젝트", "https://cdn/x.png", "메이커")));
        when(fulfillmentStatusClient.fetchBatch(List.of())).thenReturn(java.util.Map.of());

        // when
        var result = orderQueryService.listMyOrders(memberId, FundingStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 20));

        // then
        OrderQueryService.OrderListItem item = result.getContent().get(0);
        assertThat(item.projectSummary().sellerDisplayName()).isEqualTo("메이커");
        assertThat(item.availableActions()).containsExactly("CANCEL");
    }

    @Test
    void 목록조회시_쿠폰할인을_배치조회해_아이템별로_반영한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(UUID.randomUUID()).memberId(memberId).projectId(projectId)
                .status(FundingStatus.PENDING).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now().plusSeconds(1800))
                .lineItems(List.of(new FundingLineItem(1L, 5L, "리워드", 2, 10_000L, List.of())))
                .createdAt(Instant.now()).build();
        when(fundingRepository.findByMemberId(any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(funding)));
        when(projectSummaryClient.getSummaries(any())).thenReturn(java.util.Map.of());
        when(fulfillmentStatusClient.fetchBatch(any())).thenReturn(java.util.Map.of());
        when(couponApplicationJpaRepository.sumDiscountAmountByFundingIdIn(List.of(1L)))
                .thenReturn(List.of(discountProjection(1L, 3_000L)));

        // when
        var result = orderQueryService.listMyOrders(memberId, FundingStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 20));

        // then
        assertThat(result.getContent().get(0).discountAmount()).isEqualTo(3_000L);
    }

    private com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaRepository.FundingDiscountProjection
            discountProjection(Long fundingId, Long totalDiscount) {
        return new com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaRepository.FundingDiscountProjection() {
            public Long getFundingId() { return fundingId; }
            public Long getTotalDiscount() { return totalDiscount; }
        };
    }

    @Test
    void 상세조회시_쿠폰할인을_반영한_최종금액을_계산한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(orderId).memberId(memberId).projectId(UUID.randomUUID())
                .status(FundingStatus.PENDING).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(3_000L).paymentExpiresAt(Instant.now().plusSeconds(1800))
                .lineItems(List.of(new FundingLineItem(1L, 5L, "리워드", 2, 10_000L, List.of())))
                .createdAt(Instant.now()).build();
        when(fundingRepository.findByPublicId(orderId)).thenReturn(Optional.of(funding));
        when(couponApplicationJpaRepository.findByFundingId(1L)).thenReturn(List.of(
                FundingCouponApplicationJpaEntity.builder().fundingId(1L).couponIssuanceId(1L).discountAmount(2_000L).build()));

        // when
        OrderQueryService.FundingDetail detail = orderQueryService.getDetail(memberId, orderId);

        // then — 20,000(리워드) + 3,000(배송비) - 2,000(할인) = 21,000
        assertThat(detail.finalAmount()).isEqualTo(21_000L);
    }

    @Test
    void GOAL_ACHIEVED_상태만_fulfillment_service를_조회해_가능한_액션을_채운다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(orderId).memberId(memberId).projectId(UUID.randomUUID())
                .status(FundingStatus.GOAL_ACHIEVED).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now().plusSeconds(1800)).lineItems(List.of())
                .createdAt(Instant.now()).build();
        when(fundingRepository.findByPublicId(orderId)).thenReturn(Optional.of(funding));
        when(couponApplicationJpaRepository.findByFundingId(1L)).thenReturn(List.of());
        when(fulfillmentStatusClient.fetch(orderId))
                .thenReturn(new FulfillmentStatusClient.FulfillmentStatus(true, true));

        // when
        OrderQueryService.FundingDetail detail = orderQueryService.getDetail(memberId, orderId);

        // then
        assertThat(detail.availableActions()).containsExactly("DEFECT_REFUND_REQUEST");
    }
}
