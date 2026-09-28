package com.fundit.project.infrastructure.persistence.pagesummary;

import com.fundit.project.domain.pagesummary.PageSummary;
import com.fundit.project.domain.pagesummary.PageSummaryRepository;
import com.fundit.project.domain.pagesummary.PageSummarySection;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** dirty 신호 upsert와 워커 저장이 서로의 컬럼을 덮지 않는지, jsonb 왕복을 실제 DB로 확인한다. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PageSummaryPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private PageSummaryRepository pageSummaryRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void 워커가_처리한_뒤_다시_수정되면_다음_주기_대상으로_잡힌다() {
        // given
        Long projectId = project();
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        pageSummaryRepository.markDirty(projectId, t0);
        assertThat(pageSummaryRepository.findDueProjectIds(t0, 100)).contains(projectId);

        PageSummary summary = pageSummaryRepository.findByProjectId(projectId).orElseThrow();
        summary.markChecked();
        pageSummaryRepository.save(summary);
        flushAndClear();
        assertThat(pageSummaryRepository.findDueProjectIds(t0, 100)).doesNotContain(projectId);

        // when — 워커가 이전 스냅샷을 다시 저장해도 새 dirty_at을 덮지 않는다
        pageSummaryRepository.markDirty(projectId, t0.plusSeconds(1));
        pageSummaryRepository.save(summary);
        flushAndClear();

        // then
        assertThat(pageSummaryRepository.findDueProjectIds(t0, 100)).contains(projectId);
        assertThat(pageSummaryRepository.findByProjectId(projectId).orElseThrow().isDirty()).isTrue();
    }

    @Test
    void 요약_결과는_jsonb로_저장되고_그대로_읽힌다() {
        // given
        Long projectId = project();
        Instant now = Instant.now();
        pageSummaryRepository.markDirty(projectId, now);
        PageSummary summary = pageSummaryRepository.findByProjectId(projectId).orElseThrow();
        summary.startRevision("hash");
        summary.requested(UUID.randomUUID(), now);
        List<PageSummarySection> sections = List.of(
                new PageSummarySection("WHAT", "무엇", "설명1"),
                new PageSummarySection("WHY", "왜", "설명2"));
        summary.succeed(sections, now);

        // when
        pageSummaryRepository.save(summary);
        flushAndClear();

        // then
        PageSummary found = pageSummaryRepository.findByProjectId(projectId).orElseThrow();
        assertThat(found.getSections()).isEqualTo(sections);
        assertThat(found.getSourceRevision()).isEqualTo(1);
        assertThat(found.isSucceeded()).isTrue();
        assertThat(pageSummaryRepository.findDueProjectIds(now, 100)).doesNotContain(projectId);
    }

    private Long project() {
        return projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .goalAmount(1_000_000L)
                .status(ProjectStatus.ONGOING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build()).getId();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
