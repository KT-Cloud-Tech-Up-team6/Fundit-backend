package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingRewardStatsPublisher;
import com.fundit.order.infrastructure.persistence.event.FundingRewardStatsEventOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.FundingRewardStatsEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 배치 서비스와 같은 트랜잭션에서 아웃박스에만 적재한다. 실제 발행은 {@link FundingRewardStatsEventOutboxWorker}가 재시도한다. */
@Component
@RequiredArgsConstructor
public class OutboxFundingRewardStatsPublisher implements FundingRewardStatsPublisher {

    private final FundingRewardStatsEventOutboxJpaRepository outboxRepository;

    @Override
    public void publishRewardStatsUpdated(RewardStatsUpdatedEvent event) {
        outboxRepository.save(FundingRewardStatsEventOutboxJpaEntity.builder()
                .projectId(event.projectId())
                .rewardStats(event.rewardStats())
                .build());
    }
}
