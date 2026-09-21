package com.fundit.order.application.funding;

import com.fundit.order.application.funding.FundingRewardStatsPublisher.RewardStatItem;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaRepository.RewardStatProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingRewardStatsBatchServiceUnitTest {

    @Mock
    private FundingJpaRepository fundingJpaRepository;
    @Mock
    private FundingLineItemJpaRepository fundingLineItemJpaRepository;
    @Mock
    private FundingRewardStatsPublisher fundingRewardStatsPublisher;

    @InjectMocks
    private FundingRewardStatsBatchService batchService;

    @Test
    void 옵션값_단위로_집계해_발행한다() {
        // given
        when(fundingLineItemJpaRepository.aggregateRewardStatsByProjectId(7L)).thenReturn(List.of(
                projection(1L, 100L, 2, 20_000L),
                projection(1L, null, 1, 9_000L)));

        // when
        batchService.recomputeOne(7L);

        // then
        ArgumentCaptor<FundingRewardStatsPublisher.RewardStatsUpdatedEvent> captor =
                ArgumentCaptor.forClass(FundingRewardStatsPublisher.RewardStatsUpdatedEvent.class);
        verify(fundingRewardStatsPublisher).publishRewardStatsUpdated(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(7L);
        assertThat(captor.getValue().rewardStats()).containsExactly(
                new RewardStatItem(1L, 100L, 2, 20_000L),
                new RewardStatItem(1L, null, 1, 9_000L));
    }

    private RewardStatProjection projection(Long rewardId, Long optionValueId, int quantity, long amount) {
        return new RewardStatProjection() {
            @Override
            public Long getRewardId() {
                return rewardId;
            }

            @Override
            public Long getOptionValueId() {
                return optionValueId;
            }

            @Override
            public Integer getTotalQuantity() {
                return quantity;
            }

            @Override
            public Long getTotalAmount() {
                return amount;
            }
        };
    }
}
