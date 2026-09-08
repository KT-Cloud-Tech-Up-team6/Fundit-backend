package com.fundit.order.application.order;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationBatchSchedulerUnitTest {

    @Mock
    private FundingRepository fundingRepository;
    @Mock
    private PaymentExpirationProcessor processor;

    @InjectMocks
    private PaymentExpirationBatchScheduler scheduler;

    private Funding fundingWithId(Long id) {
        return Funding.builder().id(id).publicId(UUID.randomUUID()).memberId(UUID.randomUUID()).projectId(10L)
                .status(FundingStatus.PENDING).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now()).lineItems(List.of()).createdAt(Instant.now()).build();
    }

    @Test
    void 대상건마다_processor를_호출한다() {
        // given
        when(fundingRepository.findPendingExpiredBefore(any())).thenReturn(List.of(fundingWithId(1L), fundingWithId(2L)));

        // when
        scheduler.run();

        // then
        verify(processor).expireOne(1L);
        verify(processor).expireOne(2L);
    }

    @Test
    void 한_건이_실패해도_나머지_건은_계속_처리된다() {
        // given
        when(fundingRepository.findPendingExpiredBefore(any())).thenReturn(List.of(fundingWithId(1L), fundingWithId(2L)));
        when(processor.expireOne(1L)).thenThrow(new RuntimeException("재고 원복 실패"));

        // when & then — 예외가 스케줄러 밖으로 전파되지 않는다
        scheduler.run();
        verify(processor).expireOne(2L);
    }
}
