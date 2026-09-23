package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * q(검색어) 없이 목록 조회 시 500(lower(bytea) does not exist)이 재현되던 회귀 검증.
 * concat 표현식의 PostgreSQL 타입 추론 문제라 Mock으로는 드러나지 않아 통합 테스트로 검증한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ProjectJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ProjectJpaRepository projectJpaRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void q가_없어도_500이_아니고_목록이_조회된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        persistProject(sellerId, "우산 프로젝트", ProjectStatus.ONGOING);

        // when
        var page = projectJpaRepository.findList(sellerId, List.of(ProjectStatus.ONGOING.name()),
                PageRequest.of(0, 8));

        // then
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void q가_있으면_제목으로_검색된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        persistProject(sellerId, "우산 프로젝트", ProjectStatus.ONGOING);
        persistProject(sellerId, "텀블러 프로젝트", ProjectStatus.ONGOING);

        // when
        var page = projectJpaRepository.findListByTitle(sellerId, List.of(ProjectStatus.ONGOING.name()), "우산",
                PageRequest.of(0, 8));

        // then
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("우산 프로젝트");
    }

    private void persistProject(UUID sellerId, String title, ProjectStatus status) {
        Instant now = Instant.now();
        projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID()).sellerId(sellerId).title(title).status(status)
                .createdAt(now).updatedAt(now).build());
    }
}
