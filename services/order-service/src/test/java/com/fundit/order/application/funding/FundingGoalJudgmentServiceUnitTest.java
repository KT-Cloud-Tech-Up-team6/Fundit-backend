package com.fundit.order.application.funding;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingGoalJudgmentServiceUnitTest {

    @Mock
    private FundingRepository fundingRepository;
    @Mock
    private FundingEventPublisher fundingEventPublisher;

    @InjectMocks
    private FundingGoalJudgmentService service;

    private Funding funding(Long id, long unitPrice, int quantity) {
        return Funding.builder().id(id).publicId(UUID.randomUUID()).memberId(UUID.randomUUID()).projectId(10L)
                .status(FundingStatus.FUNDING_IN_PROGRESS)
                .shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now())
                .lineItems(List.of(new FundingLineItem(1L, 5L, "리워드", quantity, unitPrice, List.of())))
                .createdAt(Instant.now()).build();
    }

    @Test
    void 목표금액을_달성하면_GOAL_ACHIEVED로_전이하고_FundingSucceeded를_발행한다() {
        // given — 누적 60,000 >= 목표 50,000
        Funding funding = funding(1L, 30_000L, 2);
        when(fundingRepository.findActiveByProjectId(10L)).thenReturn(List.of(funding));
        when(fundingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        service.onProjectFundingDeadlineReached(
                new FundingDeadlineEventListener.ProjectFundingDeadlineReachedEvent(10L, 50_000L));

        // then
        assertThat(funding.getStatus()).isEqualTo(FundingStatus.GOAL_ACHIEVED);
        verify(fundingEventPublisher).publishFundingSucceeded(new FundingEventPublisher.FundingSucceededEvent(1L, 10L));
    }

    @Test
    void 목표금액에_미달하면_GOAL_FAILED_REFUNDED로_전이하고_FundingGoalFailed를_발행한다() {
        // given — 누적 10,000 < 목표 50,000
        Funding funding = funding(1L, 10_000L, 1);
        when(fundingRepository.findActiveByProjectId(10L)).thenReturn(List.of(funding));
        when(fundingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        service.onProjectFundingDeadlineReached(
                new FundingDeadlineEventListener.ProjectFundingDeadlineReachedEvent(10L, 50_000L));

        // then
        assertThat(funding.getStatus()).isEqualTo(FundingStatus.GOAL_FAILED_REFUNDED);
        verify(fundingEventPublisher).publishFundingGoalFailed(new FundingEventPublisher.FundingGoalFailedEvent(1L, 10L));
    }
}
