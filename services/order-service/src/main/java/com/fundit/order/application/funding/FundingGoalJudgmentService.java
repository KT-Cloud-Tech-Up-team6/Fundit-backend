package com.fundit.order.application.funding;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ORDER-006 — 누적 펀딩금액과 목표금액을 비교해 성립/미성립을 판정하고, 대상 Funding 각각의
 * 상태를 전이시킨 뒤 FundingSucceeded/FundingGoalFailed 이벤트를 발행한다.
 */
@Service
@RequiredArgsConstructor
public class FundingGoalJudgmentService implements FundingDeadlineEventListener {

    private static final Logger log = LoggerFactory.getLogger(FundingGoalJudgmentService.class);

    private final FundingRepository fundingRepository;
    private final FundingEventPublisher fundingEventPublisher;

    @Override
    @Transactional
    public void onProjectFundingDeadlineReached(ProjectFundingDeadlineReachedEvent event) {
        List<Funding> activeFundings = fundingRepository.findActiveByProjectId(event.projectId());
        long currentAmount = activeFundings.stream().mapToLong(Funding::totalRewardAmount).sum();
        boolean achieved = currentAmount >= event.goalAmount();

        log.info("목표달성 판정. projectId={}, currentAmount={}, goalAmount={}, achieved={}, 대상건수={}",
                event.projectId(), currentAmount, event.goalAmount(), achieved, activeFundings.size());

        for (Funding funding : activeFundings) {
            if (achieved) {
                funding.markGoalAchieved();
                fundingRepository.save(funding);
                fundingEventPublisher.publishFundingSucceeded(
                        new FundingEventPublisher.FundingSucceededEvent(funding.getId(), event.projectId()));
            } else {
                funding.markGoalFailed();
                fundingRepository.save(funding);
                fundingEventPublisher.publishFundingGoalFailed(
                        new FundingEventPublisher.FundingGoalFailedEvent(funding.getId(), event.projectId()));
            }
        }
    }
}
