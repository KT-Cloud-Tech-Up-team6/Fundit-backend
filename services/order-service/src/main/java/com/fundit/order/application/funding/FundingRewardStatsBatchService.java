package com.fundit.order.application.funding;

import com.fundit.order.application.funding.FundingRewardStatsPublisher.RewardStatItem;
import com.fundit.order.application.funding.FundingRewardStatsPublisher.RewardStatsUpdatedEvent;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PROJECT-015 — 프로젝트별 리워드 구매 통계를 하루 한 번 재계산해 아웃박스에 적재한다
 * (PRD 7.1.3 "데이터 갱신 주기: 1일"). 프로젝트마다 별도 트랜잭션으로 처리해 한 건이 실패해도
 * 나머지 프로젝트 집계에 영향을 주지 않는다.
 */
@Component
@RequiredArgsConstructor
public class FundingRewardStatsBatchService {

    private final FundingJpaRepository fundingJpaRepository;
    private final FundingLineItemJpaRepository fundingLineItemJpaRepository;
    private final FundingRewardStatsPublisher fundingRewardStatsPublisher;

    @Scheduled(cron = "${funding-reward-stats-batch.cron:0 0 3 * * *}")
    public void recomputeAll() {
        for (UUID projectId : fundingJpaRepository.findDistinctProjectPublicIdsWithCountableFundings()) {
            recomputeOne(projectId);
        }
    }

    @Transactional
    public void recomputeOne(UUID projectId) {
        var rewardStats = fundingLineItemJpaRepository.aggregateRewardStatsByProjectId(projectId).stream()
                .map(p -> new RewardStatItem(p.getRewardId(), p.getOptionValueId(), p.getTotalQuantity(), p.getTotalAmount()))
                .toList();
        fundingRewardStatsPublisher.publishRewardStatsUpdated(new RewardStatsUpdatedEvent(projectId, rewardStats));
    }
}
