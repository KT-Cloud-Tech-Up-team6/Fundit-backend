package com.fundit.order.application.order;

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

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Test
    void 목록조회는_리포지토리에_위임한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(fundingRepository.findByMemberId(any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        // when
        var result = orderQueryService.listMyOrders(memberId, FundingStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 20));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 상세조회시_쿠폰할인을_반영한_최종금액을_계산한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(orderId).memberId(memberId).projectId(10L)
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
}
