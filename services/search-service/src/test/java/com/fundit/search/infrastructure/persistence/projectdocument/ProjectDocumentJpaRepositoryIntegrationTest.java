package com.fundit.search.infrastructure.persistence.projectdocument;

import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSortType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Autowired
    private EntityManager entityManager;

    private ProjectDocumentJpaEntity project(long id, String title, ProjectDocumentStatus status, int participantCount) {
        return project(id, title, "판매자" + id, status, participantCount);
    }

    private ProjectDocumentJpaEntity project(
            long id, String title, String sellerDisplayName, ProjectDocumentStatus status, int participantCount) {
        return project(id, title, sellerDisplayName, status, participantCount, Instant.now().plus(5, ChronoUnit.DAYS));
    }

    private ProjectDocumentJpaEntity project(long id, String title, Instant fundingDeadline) {
        return project(id, title, "판매자" + id, ProjectDocumentStatus.ONGOING, 0, fundingDeadline);
    }

    private ProjectDocumentJpaEntity project(long id, String title, String sellerDisplayName,
                                             ProjectDocumentStatus status, int participantCount, Instant fundingDeadline) {
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
                .fundingDeadline(fundingDeadline)
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

    @Test
    void 홈_마감순은_마감_지난_것을_빼고_마감_가까운_순이다() {
        // given
        Instant now = Instant.now();
        projectDocumentJpaRepository.save(project(11L, "사흘 남음", now.plus(3, ChronoUnit.DAYS)));
        projectDocumentJpaRepository.save(project(12L, "하루 남음", now.plus(1, ChronoUnit.DAYS)));
        projectDocumentJpaRepository.save(project(13L, "마감 지남", now.minus(1, ChronoUnit.DAYS)));

        // when
        var result = projectDocumentJpaRepository.findByStatusAndDeletedAtIsNullAndFundingDeadlineGreaterThanEqual(
                ProjectDocumentStatus.ONGOING, now, PageRequest.of(0, 20, ProjectSortType.DEADLINE.toSort()));

        // then
        assertThat(result).extracting(ProjectCardProjection::getProjectId).containsExactly(12L, 11L);
    }

    @Test
    void 테스트_DB는_UTF8_로케일이다() {
        // 아래 한글 부분일치 테스트의 전제. LC_CTYPE=C인 DB에서는 한글 검색어가 항상 0점이라 검색되지 않는다
        String ctype = (String) entityManager.createNativeQuery(
                "SELECT datctype FROM pg_database WHERE datname = current_database()").getSingleResult();

        assertThat(ctype.toLowerCase()).containsAnyOf("utf8", "utf-8");
    }

    @ParameterizedTest
    @ValueSource(strings = {"무선청소기", "청소기", "무선 청소기"})
    void 긴_한글_제목_안에_검색어가_있으면_검색된다(String keyword) {
        // given — 제목 전체와 비교하는 similarity로는 잡히지 않던 경우
        projectDocumentJpaRepository.save(project(21L, "[청소가 가벼워진다] 꺼내는 순간 쓱! 데일리 무선청소기",
                ProjectDocumentStatus.ONGOING, 0));

        // when
        var result = projectDocumentJpaRepository.searchByKeyword(
                keyword, List.of(ProjectDocumentStatus.ONGOING), PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).extracting(ProjectCardProjection::getProjectId).containsExactly(21L);
    }

    @Test
    void 관계없는_검색어는_걸리지_않는다() {
        // given
        projectDocumentJpaRepository.save(project(22L, "[청소가 가벼워진다] 꺼내는 순간 쓱! 데일리 무선청소기",
                ProjectDocumentStatus.ONGOING, 0));

        // when
        var result = projectDocumentJpaRepository.searchByKeyword(
                "캠핑의자", List.of(ProjectDocumentStatus.ONGOING), PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void 판매자명으로도_검색된다() {
        // given
        projectDocumentJpaRepository.save(project(23L, "데일리 무선청소기", "쓱쓱생활연구소",
                ProjectDocumentStatus.ONGOING, 0));

        // when
        var result = projectDocumentJpaRepository.searchByKeyword(
                "생활연구소", List.of(ProjectDocumentStatus.ONGOING), PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).extracting(ProjectCardProjection::getProjectId).containsExactly(23L);
    }
}
