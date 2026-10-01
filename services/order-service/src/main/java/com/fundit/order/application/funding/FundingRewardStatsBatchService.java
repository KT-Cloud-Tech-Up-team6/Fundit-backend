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
 * PROJECT-015 — 프로젝트별 리워드 구매 통계를 재계산해 아웃박스에 적재한다(PRD 7.1.3 개정: 실시간).
 *
 * <p>평소에는 {@link #recomputeOne}이 결제완료·참여취소·성립후환불 시점에 호출돼 바로 반영된다(#230).
 * {@link #recomputeAll}은 그 호출이 누락되거나 발행에 실패했을 때 다음 날 정합성을 맞춰 주는
 * 안전망이다 — 전체 교체라 중복 실행돼도 같은 스냅샷으로 수렴한다. 프로젝트마다 별도 트랜잭션으로
 * 처리해 한 건이 실패해도 나머지 프로젝트 집계에 영향을 주지 않는다.
 */
@Component
@RequiredArgsConstructor
public class FundingRewardStatsBatchService {

    private final FundingJpaRepository fundingJpaRepository;
    private final FundingLineItemJpaRepository fundingLineItemJpaRepository;
    private final FundingRewardStatsPublisher fundingRewardStatsPublisher;

    /** 안전망 — 평소 반영은 {@link #recomputeOne} 호출이 담당한다(#230). */
    @Scheduled(cron = "${funding-reward-stats-batch.cron:0 0 3 * * *}")
    public void recomputeAll() {
        for (UUID projectId : fundingJpaRepository.findDistinctProjectPublicIdsWithCountableFundings()) {
            recomputeOne(projectId);
        }
    }

    /**
     * 호출부의 트랜잭션에 합류한다(REQUIRED) — 상태 변경과 아웃박스 적재가 원자적이어야 하기 때문이다.
     * 집계가 네이티브 쿼리라 호출부는 반드시 {@code save()} <b>뒤에</b> 불러야 바뀐 status가 보인다.
     */
    @Transactional
    public void recomputeOne(UUID projectId) {
        var rewardStats = fundingLineItemJpaRepository.aggregateRewardStatsByProjectId(projectId).stream()
                .map(p -> new RewardStatItem(p.getRewardId(), p.getOptionValueId(), p.getTotalQuantity(), p.getTotalAmount()))
                .toList();
        int participantCount = fundingJpaRepository.countParticipantsByProjectPublicId(projectId);
        fundingRewardStatsPublisher.publishRewardStatsUpdated(
                new RewardStatsUpdatedEvent(projectId, rewardStats, participantCount));
    }
}
