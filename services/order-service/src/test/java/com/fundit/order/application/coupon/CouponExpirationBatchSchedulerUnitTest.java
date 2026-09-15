package com.fundit.order.application.coupon;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponExpirationBatchSchedulerUnitTest {

    @Mock
    private CouponIssuanceRepository couponIssuanceRepository;
    @Mock
    private CouponExpirationProcessor processor;

    @InjectMocks
    private CouponExpirationBatchScheduler scheduler;

    private CouponIssuance issuanceWithId(Long id) {
        return CouponIssuance.issue("CODE", UUID.randomUUID()).toBuilder().id(id).build();
    }

    @Test
    void 대상건마다_processor를_호출한다() {
        // given
        when(couponIssuanceRepository.findAvailableExpired(any())).thenReturn(List.of(issuanceWithId(1L), issuanceWithId(2L)));

        // when
        scheduler.run();

        // then
        verify(processor).expireOne(1L);
        verify(processor).expireOne(2L);
    }

    @Test
    void 한_건이_실패해도_나머지_건은_계속_처리된다() {
        // given
        when(couponIssuanceRepository.findAvailableExpired(any())).thenReturn(List.of(issuanceWithId(1L), issuanceWithId(2L)));
        when(processor.expireOne(1L)).thenThrow(new RuntimeException("DB 오류"));

        // when & then — 예외가 스케줄러 밖으로 전파되지 않는다
        scheduler.run();
        verify(processor).expireOne(2L);
    }
}
