package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FundingDeadlineWatcher가 쓰는 파생 쿼리(findOngoingWithDeadlineReached)가 상태/마감시각/통지여부/
 * 소프트삭제 조건을 실제 DB에서 정확히 걸러내는지 검증한다 — Spring Data 파생 쿼리는 이름을 잘못
 * 지어도 컴파일은 통과하고 조용히 다른 결과를 내놓기 때문에(event-convention.md 토픽명 오탈자와 같은 부류의 위험).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ProjectPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ProjectRepository projectRepository;

    private Project.ProjectBuilder base() {
        return Project.builder()
                .publicId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .goalAmount(1_000_000L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now());
    }

    @Test
    void 마감이_지난_진행중_미통지_프로젝트만_조회된다() {
        // given — 대상(마감 지남, ONGOING, 미통지)
        Project target = projectRepository.save(base()
                .status(ProjectStatus.ONGOING)
                .fundingDeadline(Instant.now().minusSeconds(60))
                .build());

        // 이미 통지됨 — 제외 대상
        Project alreadyNotified = base()
                .status(ProjectStatus.ONGOING)
                .fundingDeadline(Instant.now().minusSeconds(60))
                .build();
        alreadyNotified = projectRepository.save(alreadyNotified);
        alreadyNotified.markDeadlineNotified();
        projectRepository.save(alreadyNotified);

        // 아직 마감 전 — 제외 대상
        projectRepository.save(base()
                .status(ProjectStatus.ONGOING)
                .fundingDeadline(Instant.now().plusSeconds(3600))
                .build());

        // DRAFT 상태 — 제외 대상
        projectRepository.save(base()
                .status(ProjectStatus.DRAFT)
                .fundingDeadline(Instant.now().minusSeconds(60))
                .build());

        // when
        var result = projectRepository.findOngoingWithDeadlineReached(Instant.now());

        // then
        assertThat(result).extracting(Project::getId).containsExactly(target.getId());
    }
}
