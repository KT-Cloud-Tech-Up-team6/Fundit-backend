package com.fundit.order.application.funding;

import com.fundit.common.error.BusinessException;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingInternalQueryServiceUnitTest {

    @Mock
    private FundingRepository fundingRepository;
    @Mock
    private FundingCouponApplicationJpaRepository couponApplicationJpaRepository;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;

    @InjectMocks
    private FundingInternalQueryService service;

    private Funding funding(Long id, UUID publicId, UUID memberId, UUID projectId) {
        return Funding.builder().id(id).publicId(publicId).memberId(memberId).projectId(projectId)
                .status(FundingStatus.GOAL_ACHIEVED)
                .shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now())
                .lineItems(List.of(new FundingLineItem(1L, 5L, "리워드", 1, 1000L, List.of())))
                .createdAt(Instant.now()).build();
    }

    @Test
    void 존재하는_펀딩이면_스냅샷을_반환한다() {
        // given
        UUID publicId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(fundingRepository.findById(1024L)).thenReturn(Optional.of(funding(1024L, publicId, memberId, projectId)));
        when(couponApplicationJpaRepository.findByFundingId(1024L)).thenReturn(List.of());
        when(projectOwnershipClient.findSellerId(projectId)).thenReturn(Optional.of(sellerId));

        // when
        var snapshot = service.getSnapshot(1024L);

        // then
        assertThat(snapshot.fundingId()).isEqualTo(1024L);
        assertThat(snapshot.projectId()).isEqualTo(projectId);
        assertThat(snapshot.memberId()).isEqualTo(memberId);
        assertThat(snapshot.fundingPublicId()).isEqualTo(publicId);
        assertThat(snapshot.sellerId()).isEqualTo(sellerId);
        assertThat(snapshot.status()).isEqualTo("GOAL_ACHIEVED");
        assertThat(snapshot.finalAmount()).isEqualTo(1000L);
        assertThat(snapshot.orderName()).isEqualTo("리워드");
        assertThat(snapshot.couponIssuanceId()).isNull();
    }

    @Test
    void 쿠폰이_적용된_펀딩이면_할인액을_반영한_최종금액과_couponIssuanceId를_반환한다() {
        // given
        UUID publicId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        FundingCouponApplicationJpaEntity application = FundingCouponApplicationJpaEntity.builder()
                .fundingId(1024L).couponIssuanceId(77L).discountAmount(300L).build();
        when(fundingRepository.findById(1024L)).thenReturn(Optional.of(funding(1024L, publicId, memberId, projectId)));
        when(couponApplicationJpaRepository.findByFundingId(1024L)).thenReturn(List.of(application));
        when(projectOwnershipClient.findSellerId(projectId)).thenReturn(Optional.empty());

        // when
        var snapshot = service.getSnapshot(1024L);

        // then
        assertThat(snapshot.finalAmount()).isEqualTo(700L);
        assertThat(snapshot.couponIssuanceId()).isEqualTo(77L);
        assertThat(snapshot.sellerId()).isNull();
    }

    @Test
    void 라인아이템이_여러개면_orderName에_외N건이_붙는다() {
        // given
        UUID publicId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Funding funding = funding(1024L, publicId, memberId, projectId).toBuilder()
                .lineItems(List.of(
                        new FundingLineItem(1L, 5L, "리워드", 1, 1000L, List.of()),
                        new FundingLineItem(2L, 6L, "다른 리워드", 1, 500L, List.of())))
                .build();
        when(fundingRepository.findById(1024L)).thenReturn(Optional.of(funding));
        when(couponApplicationJpaRepository.findByFundingId(1024L)).thenReturn(List.of());
        when(projectOwnershipClient.findSellerId(any(UUID.class))).thenReturn(Optional.empty());

        // when
        var snapshot = service.getSnapshot(1024L);

        // then
        assertThat(snapshot.orderName()).isEqualTo("리워드 외 1건");
    }

    @Test
    void orderId로_조회하면_스냅샷을_반환한다() {
        // given
        UUID publicId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(fundingRepository.findByPublicId(publicId)).thenReturn(Optional.of(
                funding(1024L, publicId, memberId, projectId)));
        when(couponApplicationJpaRepository.findByFundingId(1024L)).thenReturn(List.of());
        when(projectOwnershipClient.findSellerId(projectId)).thenReturn(Optional.empty());

        // when
        var snapshot = service.getSnapshotByOrderId(publicId);

        // then
        assertThat(snapshot.fundingId()).isEqualTo(1024L);
        assertThat(snapshot.fundingPublicId()).isEqualTo(publicId);
        assertThat(snapshot.projectId()).isEqualTo(projectId);
    }

    @Test
    void 존재하지_않는_펀딩이면_예외가_발생한다() {
        // given
        when(fundingRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getSnapshot(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 펀딩이_성립된_참여자의_memberId_목록을_반환한다() {
        // given
        UUID projectId = UUID.randomUUID();
        UUID memberId1 = UUID.randomUUID();
        UUID memberId2 = UUID.randomUUID();
        when(fundingRepository.findGoalAchievedByProjectId(projectId)).thenReturn(List.of(
                funding(1L, UUID.randomUUID(), memberId1, projectId),
                funding(2L, UUID.randomUUID(), memberId2, projectId)));

        // when
        var memberIds = service.listGoalAchievedParticipantMemberIds(projectId);

        // then
        assertThat(memberIds).containsExactlyInAnyOrder(memberId1, memberId2);
    }
}
