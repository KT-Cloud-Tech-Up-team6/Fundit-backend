package com.fundit.project.application.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 펀딩 마감 시각(funding_deadline) 도래를 감지해 order-service에 통지하는 배치.
 * 감지 대상마다 {@link Project#markDeadlineNotified()}로 통지 표시를 남기고 아웃박스에 적재하는
 * 것을 한 트랜잭션으로 처리해, 같은 프로젝트가 다음 폴링 주기에 다시 잡혀 중복 발행되지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class FundingDeadlineWatcher {

    private static final Logger log = LoggerFactory.getLogger(FundingDeadlineWatcher.class);

    private final ProjectRepository projectRepository;
    private final FundingDeadlinePublisher fundingDeadlinePublisher;

    @Scheduled(fixedDelayString = "${funding-deadline-watcher.poll-interval-ms:60000}")
    @Transactional
    public void detectReachedDeadlines() {
        for (Project project : projectRepository.findOngoingWithDeadlineReached(Instant.now())) {
            fundingDeadlinePublisher.publishFundingDeadlineReached(
                    new FundingDeadlinePublisher.FundingDeadlineReachedEvent(project.getId(), project.getGoalAmount()));
            project.markDeadlineNotified();
            projectRepository.save(project);
            log.info("펀딩 마감 도래 통지 적재. projectId={}, goalAmount={}", project.getId(), project.getGoalAmount());
        }
    }
}
