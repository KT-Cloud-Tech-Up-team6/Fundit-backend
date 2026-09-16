package com.fundit.order.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.order.application.funding.FundingDeadlineEventListener;
import com.fundit.order.application.funding.FundingDeadlineEventListener.ProjectFundingDeadlineReachedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ORDER-006 — project-service가 발행할 펀딩 마감 도래 이벤트를 구독해
 * {@link FundingDeadlineEventListener}(={@code FundingGoalJudgmentService})로 위임하는
 * 얇은 어댑터. project-service가 아직 이 토픽으로 발행하지 않아(Tier C, `.claude/plans/` 참고)
 * 지금은 이 어댑터를 붙여도 실제 트래픽이 없다 — 발행 측이 준비되면 바로 동작한다.
 */
@Component
@RequiredArgsConstructor
public class FundingDeadlineEventKafkaListener {

    private final FundingDeadlineEventListener listener;

    @KafkaListener(topics = KafkaTopics.PROJECT_FUNDING_DEADLINE_REACHED, groupId = "order-service")
    public void onProjectFundingDeadlineReached(ProjectFundingDeadlineReachedEvent event) {
        listener.onProjectFundingDeadlineReached(event);
    }
}
