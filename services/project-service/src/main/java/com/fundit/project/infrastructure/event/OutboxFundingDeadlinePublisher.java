package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.FundingDeadlinePublisher;
import com.fundit.project.infrastructure.persistence.event.FundingDeadlineEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.FundingDeadlineEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FundingDeadlineWatcher와 같은 트랜잭션에서 아웃박스에만 적재한다. 실제 발행은
 * {@link FundingDeadlineEventOutboxWorker}가 재시도하며, 로깅만으로 성공 처리하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class OutboxFundingDeadlinePublisher implements FundingDeadlinePublisher {

    private final FundingDeadlineEventOutboxJpaRepository outboxRepository;

    @Override
    public void publishFundingDeadlineReached(FundingDeadlineReachedEvent event) {
        outboxRepository.save(FundingDeadlineEventOutboxJpaEntity.builder()
                .projectId(event.projectId())
                .goalAmount(event.goalAmount())
                .build());
    }
}
