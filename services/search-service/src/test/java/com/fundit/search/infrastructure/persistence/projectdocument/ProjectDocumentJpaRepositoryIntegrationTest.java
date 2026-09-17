package com.fundit.search.infrastructure.persistence.projectdocument;

import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSortType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * pg_trgm 유사도 검색은 실제 Postgres extension이 있어야만 검증된다 — JPQL의 {@code function('similarity', ...)}
 * 호출이 문법적으로만 맞고 실제 인덱스/함수가 없는 DB에서는 통과 여부를 알 수 없기 때문이다.
 *
 * <p>internal-api.key 고정 이유는 NotificationJpaRepositoryIntegrationTest와 동일
 * (spring.profiles.active=local이 gitignore된 파일을 가리켜 CI에서 컨텍스트 로딩이 실패하는 것을 막는다).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class ProjectDocumentJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ProjectDocumentJpaRepository projectDocumentJpaRepository;

    private ProjectDocumentJpaEntity project(long id, String title, ProjectDocumentStatus status, int participantCount) {
        return project(id, title, "판매자" + id, status, participantCount);
    }

    private ProjectDocumentJpaEntity project(
            long id, String title, String sellerDisplayName, ProjectDocumentStatus status, int participantCount) {
        return ProjectDocumentJpaEntity.builder()
                .projectId(id)
                .projectPublicId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .sellerDisplayName(sellerDisplayName)
                .title(title)
                .categoryMajor("테크·가전")
                .categoryMinor("생활가전")
                .status(status)
                .goalAmount(1_000_000L)
                .fundingDeadline(Instant.now().plus(5, ChronoUnit.DAYS))
                .projectCreatedAt(Instant.now())
                .currentAmount(0L)
                .achievementRate(0)
                .participantCount(participantCount)
                .wishCount(0)
                .indexedAt(Instant.now())
                .build();
    }

    @Test
    void 제목이_비슷하면_오탈자여도_검색된다() {
        // given — pg_trgm 유사도 매칭이므로 완전 일치가 아니어도 잡혀야 한다
        projectDocumentJpaRepository.save(project(1L, "세상에 없는 후라이팬", ProjectDocumentStatus.ONGOING, 10));
        projectDocumentJpaRepository.save(project(2L, "무선 이어폰", ProjectDocumentStatus.ONGOING, 5));

        // when
        var result = projectDocumentJpaRepository.searchByKeyword(
                "프라이팬", List.of(ProjectDocumentStatus.ONGOING),
                PageRequest.of(0, 20, ProjectSortType.POPULAR.toSort()));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getProjectId()).isEqualTo(1L);
    }

    @Test
    void 종료_상태만_조회하면_진행중_프로젝트는_제외된다() {
        // given
        projectDocumentJpaRepository.save(project(3L, "캠핑 의자", ProjectDocumentStatus.ONGOING, 1));
        projectDocumentJpaRepository.save(project(4L, "캠핑 의자 시즌2", ProjectDocumentStatus.SUCCEEDED, 1));

        // when
        var result = projectDocumentJpaRepository.searchByKeyword(
                "캠핑 의자", List.of(ProjectDocumentStatus.SUCCEEDED, ProjectDocumentStatus.FAILED),
                PageRequest.of(0, 20, ProjectSortType.POPULAR.toSort()));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getProjectId()).isEqualTo(4L);
    }

    @Test
    void 인기순_정렬은_참여자수_내림차순이다() {
        // given
        projectDocumentJpaRepository.save(project(5L, "무선 이어폰 A", ProjectDocumentStatus.ONGOING, 3));
        projectDocumentJpaRepository.save(project(6L, "무선 이어폰 B", ProjectDocumentStatus.ONGOING, 30));

        // when
        var result = projectDocumentJpaRepository.searchByKeyword(
                "무선 이어폰", List.of(ProjectDocumentStatus.ONGOING),
                PageRequest.of(0, 20, ProjectSortType.POPULAR.toSort()));

        // then
        assertThat(result.getContent().getFirst().getProjectId()).isEqualTo(6L);
    }
}
