package com.fundit.project.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.project.application.project.ProjectFundingRewardStatsUpdatedEvent;
import com.fundit.project.application.project.ProjectStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** PROJECT-015 — order-service 리워드 옵션 집계를 받아 스냅샷 reward_stats를 교체한다. */
@Component
@RequiredArgsConstructor
public class FundingRewardStatsKafkaListener {

    private final ProjectStatsService projectStatsService;

    @KafkaListener(topics = KafkaTopics.PROJECT_FUNDING_REWARD_STATS_UPDATED, groupId = "project-service")
    public void onRewardStatsUpdated(ProjectFundingRewardStatsUpdatedEvent event) {
        if (event.projectId() == null) {
            return;
        }
        projectStatsService.applyRewardStats(event.projectId(),
                event.rewardStats() == null ? List.of() : event.rewardStats());
    }
}
