package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingEventPublisher;
import com.fundit.order.infrastructure.persistence.event.FundingEventOutboxJpaEntity;
import com.fundit.order.infrastructure.persistence.event.FundingEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 펀딩 상태 변경과 같은 트랜잭션에서 아웃박스에만 적재한다. 실제 발행은
 * {@link FundingEventOutboxWorker}가 재시도하며, 로깅만으로 성공 처리하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxFundingEventPublisher implements FundingEventPublisher {

    private final FundingEventOutboxJpaRepository outboxRepository;

    @Override
    public void publishFundingGoalFailed(FundingGoalFailedEvent event) {
        outboxRepository.save(toEntity(FundingEventOutboxJpaEntity.TYPE_GOAL_FAILED,
                event.fundingId(), event.projectId(), null));
    }

    @Override
    public void publishFundingSucceeded(FundingSucceededEvent event) {
        outboxRepository.save(toEntity(FundingEventOutboxJpaEntity.TYPE_SUCCEEDED,
                event.fundingId(), event.projectId(), null));
    }

    @Override
    public void publishFundingCancelledByMember(FundingCancelledByMemberEvent event) {
        outboxRepository.save(toEntity(FundingEventOutboxJpaEntity.TYPE_CANCELLED_BY_MEMBER,
                event.fundingId(), event.projectId(), event.memberId()));
    }

    private static FundingEventOutboxJpaEntity toEntity(String eventType, Long fundingId, Long projectId,
                                                          java.util.UUID memberId) {
        return FundingEventOutboxJpaEntity.builder()
                .eventType(eventType)
                .fundingId(fundingId)
                .projectId(projectId)
                .memberId(memberId)
                .build();
    }
}
