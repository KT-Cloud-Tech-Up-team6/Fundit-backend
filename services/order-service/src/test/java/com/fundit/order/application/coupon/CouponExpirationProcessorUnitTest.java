package com.fundit.order.application.coupon;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponExpirationProcessorUnitTest {

    @Mock
    private CouponIssuanceRepository couponIssuanceRepository;

    @InjectMocks
    private CouponExpirationProcessor processor;

    @Test
    void AVAILABLE이면_만료처리한다() {
        // given
        CouponIssuance issuance = CouponIssuance.issue("CODE", UUID.randomUUID()).toBuilder().id(1L).build();
        when(couponIssuanceRepository.findById(1L)).thenReturn(Optional.of(issuance));
        when(couponIssuanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        boolean expired = processor.expireOne(1L);

        // then
        assertThat(expired).isTrue();
        assertThat(issuance.getStatus()).isEqualTo(com.fundit.order.domain.coupon.CouponIssuanceStatus.EXPIRED);
        verify(couponIssuanceRepository).save(issuance);
    }

    @Test
    void 이미_다른_상태면_아무일도_하지않는다() {
        // given
        CouponIssuance issuance = CouponIssuance.issue("CODE", UUID.randomUUID()).toBuilder().id(1L).build();
        issuance.markUsed(100L);
        when(couponIssuanceRepository.findById(1L)).thenReturn(Optional.of(issuance));

        // when
        boolean expired = processor.expireOne(1L);

        // then
        assertThat(expired).isFalse();
        verify(couponIssuanceRepository, never()).save(any());
    }

    @Test
    void 존재하지_않으면_false를_반환한다() {
        // given
        when(couponIssuanceRepository.findById(999L)).thenReturn(Optional.empty());

        // when
        boolean expired = processor.expireOne(999L);

        // then
        assertThat(expired).isFalse();
    }
}
