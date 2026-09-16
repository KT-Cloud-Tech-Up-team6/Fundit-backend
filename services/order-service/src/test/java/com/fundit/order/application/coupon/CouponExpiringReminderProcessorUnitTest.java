package com.fundit.order.application.coupon;

import com.fundit.order.application.notification.OrderNotificationPublisher;
import com.fundit.order.application.notification.OrderNotificationPublisher.CouponExpiringEvent;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class CouponExpiringReminderProcessorUnitTest {

    @Mock
    private CouponIssuanceRepository couponIssuanceRepository;
    @Mock
    private OrderNotificationPublisher notificationPublisher;

    @InjectMocks
    private CouponExpiringReminderProcessor processor;

    @Test
    void AVAILABLE이고_미발송이면_리마인더를_적재하고_발송_표시한다() {
        // given
        UUID ownerId = UUID.randomUUID();
        CouponIssuance issuance = CouponIssuance.issue("CODE", ownerId).toBuilder().id(1L).build();
        when(couponIssuanceRepository.findById(1L)).thenReturn(Optional.of(issuance));
        when(couponIssuanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        boolean reminded = processor.remindOne(1L);

        // then
        assertThat(reminded).isTrue();
        assertThat(issuance.getExpiringNotifiedAt()).isNotNull();
        verify(couponIssuanceRepository).save(issuance);
        ArgumentCaptor<CouponExpiringEvent> captor = ArgumentCaptor.forClass(CouponExpiringEvent.class);
        verify(notificationPublisher).publishCouponExpiring(captor.capture());
        assertThat(captor.getValue().couponIssuanceId()).isEqualTo(1L);
        assertThat(captor.getValue().memberId()).isEqualTo(ownerId);
    }

    @Test
    void 이미_발송했으면_다시_적재하지_않는다() {
        // given
        CouponIssuance issuance = CouponIssuance.issue("CODE", UUID.randomUUID()).toBuilder().id(1L).build();
        issuance.markExpiringNotified();
        when(couponIssuanceRepository.findById(1L)).thenReturn(Optional.of(issuance));

        // when
        boolean reminded = processor.remindOne(1L);

        // then
        assertThat(reminded).isFalse();
        verify(couponIssuanceRepository, never()).save(any());
        verify(notificationPublisher, never()).publishCouponExpiring(any());
    }

    @Test
    void AVAILABLE이_아니면_적재하지_않는다() {
        // given
        CouponIssuance issuance = CouponIssuance.issue("CODE", UUID.randomUUID()).toBuilder().id(1L).build();
        issuance.markUsed(100L);
        when(couponIssuanceRepository.findById(1L)).thenReturn(Optional.of(issuance));

        // when
        boolean reminded = processor.remindOne(1L);

        // then
        assertThat(reminded).isFalse();
        verify(notificationPublisher, never()).publishCouponExpiring(any());
    }

    @Test
    void 존재하지_않으면_false를_반환한다() {
        // given
        when(couponIssuanceRepository.findById(999L)).thenReturn(Optional.empty());

        // when
        boolean reminded = processor.remindOne(999L);

        // then
        assertThat(reminded).isFalse();
    }
}
