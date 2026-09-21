package com.fundit.project.application.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 펀딩 마감 시각(funding_deadline) 도래를 감지해 order-service에 통지하는 배치.
 * 감지 대상마다 {@link Project#markDeadlineNotified()}로 통지 표시를 남기고 아웃박스에 적재하는
 * 것을 한 트랜잭션으로 처리해, 같은 프로젝트가 다음 폴링 주기에 다시 잡혀 중복 발행되지 않게 한다.
 *
 * <p>한 주기에 마감이 몰려도(인기 카테고리 동시 오픈 등) 한 트랜잭션에서 전부 읽지 않도록
 * {@code batchSize}로 상한을 둔다 — 남은 건은 다음 폴링 주기(기본 1분)에 마저 처리된다
 * (RewardEventOutboxWorker와 동일하게 "한 틱에 한 배치" 원칙).
 */
@Component
public class FundingDeadlineWatcher {

    private static final Logger log = LoggerFactory.getLogger(FundingDeadlineWatcher.class);

    private final ProjectRepository projectRepository;
    private final FundingDeadlinePublisher fundingDeadlinePublisher;
    private final int batchSize;

    public FundingDeadlineWatcher(ProjectRepository projectRepository,
                                   FundingDeadlinePublisher fundingDeadlinePublisher,
                                   @Value("${funding-deadline-watcher.batch-size:200}") int batchSize) {
        this.projectRepository = projectRepository;
        this.fundingDeadlinePublisher = fundingDeadlinePublisher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${funding-deadline-watcher.poll-interval-ms:60000}")
    @Transactional
    public void detectReachedDeadlines() {
        for (Project project : projectRepository.findOngoingWithDeadlineReached(Instant.now(), PageRequest.of(0, batchSize))) {
            fundingDeadlinePublisher.publishFundingDeadlineReached(
                    new FundingDeadlinePublisher.FundingDeadlineReachedEvent(project.getId(), project.getGoalAmount()));
            project.markDeadlineNotified();
            projectRepository.save(project);
            log.info("펀딩 마감 도래 통지 적재. projectId={}, goalAmount={}", project.getId(), project.getGoalAmount());
        }
    }
}
