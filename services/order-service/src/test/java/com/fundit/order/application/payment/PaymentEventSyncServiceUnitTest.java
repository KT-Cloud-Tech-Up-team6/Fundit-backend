package com.fundit.order.application.payment;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventSyncServiceUnitTest {

    @Mock
    private FundingRepository fundingRepository;
    @Mock
    private CouponIssuanceRepository couponIssuanceRepository;

    @InjectMocks
    private PaymentEventSyncService paymentEventSyncService;

    private Funding funding(Long id, FundingStatus status) {
        return Funding.builder().id(id).publicId(UUID.randomUUID()).memberId(UUID.randomUUID()).projectId(10L)
                .status(status).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now()).lineItems(List.of()).createdAt(Instant.now()).build();
    }

    @Test
    void 결제완료시_PENDING_주문을_FUNDING_IN_PROGRESS로_전환하고_쿠폰을_사용완료처리한다() {
        // given
        Funding funding = funding(1L, FundingStatus.PENDING);
        CouponIssuance issuance = CouponIssuance.issue("WELCOME", UUID.randomUUID());
        when(fundingRepository.findById(1L)).thenReturn(Optional.of(funding));
        when(fundingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(couponIssuanceRepository.findById(5L)).thenReturn(Optional.of(issuance));
        when(couponIssuanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        paymentEventSyncService.onPaymentCompleted(new PaymentEventListener.PaymentCompletedEvent(1L, 5L));

        // then
        assertThat(funding.getStatus()).isEqualTo(FundingStatus.FUNDING_IN_PROGRESS);
        assertThat(issuance.getStatus()).isEqualTo(com.fundit.order.domain.coupon.CouponIssuanceStatus.USED);
        assertThat(issuance.getUsedFundingId()).isEqualTo(1L);
    }

    @Nested
    class 환불_처리 {

        @Test
        void 목표미달_자동환불이면_쿠폰을_복원한다() {
            // given
            CouponIssuance issuance = CouponIssuance.issue("WELCOME", UUID.randomUUID());
            issuance.markUsed(1L);
            when(couponIssuanceRepository.findById(5L)).thenReturn(Optional.of(issuance));
            when(couponIssuanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // when
            paymentEventSyncService.onRefundCompleted(new PaymentEventListener.RefundCompletedEvent(
                    1L, 5L, PaymentEventListener.RefundReason.GOAL_FAILURE_AUTO_REFUND, true));

            // then
            assertThat(issuance.isAvailable()).isTrue();
        }

        @Test
        void 마감전_단순변심_취소는_쿠폰을_복원하지_않는다() {
            // when
            paymentEventSyncService.onRefundCompleted(new PaymentEventListener.RefundCompletedEvent(
                    1L, 5L, PaymentEventListener.RefundReason.CANCELLED_BY_MEMBER, true));

            // then
            verify(couponIssuanceRepository, never()).findById(any());
        }

        @Test
        void 성립후_하자환불이_전액환불이면_쿠폰복원과_함께_펀딩상태가_REFUNDED_AFTER_SUCCESS가_된다() {
            // given
            CouponIssuance issuance = CouponIssuance.issue("WELCOME", UUID.randomUUID());
            issuance.markUsed(1L);
            Funding funding = funding(1L, FundingStatus.GOAL_ACHIEVED);
            when(couponIssuanceRepository.findById(5L)).thenReturn(Optional.of(issuance));
            when(couponIssuanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(fundingRepository.findById(1L)).thenReturn(Optional.of(funding));
            when(fundingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // when
            paymentEventSyncService.onRefundCompleted(new PaymentEventListener.RefundCompletedEvent(
                    1L, 5L, PaymentEventListener.RefundReason.POST_SUCCESS_DEFECT, true));

            // then
            assertThat(issuance.isAvailable()).isTrue();
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.REFUNDED_AFTER_SUCCESS);
        }

        @Test
        void 성립후_하자환불이_부분환불이면_쿠폰을_복원하지않고_펀딩상태도_바꾸지않는다() {
            // when
            paymentEventSyncService.onRefundCompleted(new PaymentEventListener.RefundCompletedEvent(
                    1L, 5L, PaymentEventListener.RefundReason.POST_SUCCESS_DEFECT, false));

            // then
            verify(couponIssuanceRepository, never()).findById(any());
            verify(fundingRepository, never()).findById(any());
        }
    }
}
