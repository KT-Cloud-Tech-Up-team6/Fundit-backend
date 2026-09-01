package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProjectPersistenceAdapter 통합")
@Sql("/sql/insert-categories.sql")
class ProjectPersistenceAdapterIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ProjectRepository projectRepository;

    private UUID sellerId;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
    }

    private Project saveDraft() {
        return projectRepository.save(Project.createDraft(sellerId));
    }

    @Nested
    class 저장과_조회 {

        @Test
        void 저장하면_식별자가_채번된다() {
            // given & when
            Project saved = saveDraft();

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getPublicId()).isNotNull();
        }

        @Test
        void 외부_식별자로_다시_찾을_수_있다() {
            // given
            Project saved = saveDraft();

            // when
            var found = projectRepository.findByPublicId(saved.getPublicId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
        }

        @Test
        void 소개_내용이_JSONB로_저장되고_그대로_돌아온다() {
            // given
            Project saved = saveDraft();
            Map<String, Object> intro = Map.of("blocks", List.of(Map.of("type", "text", "value", "본문")));
            saved.updateDetail("제목", "https://cdn.example.com/a.jpg", intro);

            // when
            projectRepository.save(saved);
            var found = projectRepository.findByPublicId(saved.getPublicId()).orElseThrow();

            // then — Map 구조가 문자열로 뭉개지지 않고 왕복하는지 확인한다
            assertThat(found.getIntroContent()).isEqualTo(intro);
        }

        @Test
        void 카테고리_복합_외래키가_있는_값도_저장된다() {
            // given
            Project saved = saveDraft();
            saved.updateBasicInfo(BusinessType.CORPORATION, "테크·가전", "음향기기", 3_000_000L, true);

            // when
            projectRepository.save(saved);
            var found = projectRepository.findByPublicId(saved.getPublicId()).orElseThrow();

            // then
            assertThat(found.getCategoryMajor()).isEqualTo("테크·가전");
            assertThat(found.getBusinessType()).isEqualTo(BusinessType.CORPORATION);
        }
    }

    @Nested
    class 소프트_삭제 {

        @Test
        void 삭제된_프로젝트는_외부_식별자로_조회되지_않는다() {
            // given
            Project saved = saveDraft();
            saved.softDelete(Instant.now());
            projectRepository.save(saved);

            // when
            var found = projectRepository.findByPublicId(saved.getPublicId());

            // then
            assertThat(found).isEmpty();
        }

        @Test
        void 삭제된_프로젝트는_내부_식별자로도_조회되지_않는다() {
            // given
            Project saved = saveDraft();
            saved.softDelete(Instant.now());
            projectRepository.save(saved);

            // when
            var found = projectRepository.findById(saved.getId());

            // then
            assertThat(found).isEmpty();
        }

        @Test
        void 삭제된_프로젝트는_목록과_건수에서_빠진다() {
            // given
            saveDraft();
            Project deleted = saveDraft();
            deleted.softDelete(Instant.now());
            projectRepository.save(deleted);

            // when
            List<Project> projects = projectRepository.findBySeller(sellerId, List.of(), 0, 20);

            // then
            assertThat(projects).hasSize(1);
            assertThat(projectRepository.countBySeller(sellerId, List.of())).isEqualTo(1L);
        }

        @Test
        void 행이_실제로_지워지지는_않는다() {
            // given
            Project saved = saveDraft();
            saved.softDelete(Instant.now());
            projectRepository.save(saved);

            // when
            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM projects WHERE id = ?", Integer.class, saved.getId());

            // then
            assertThat(rows).isEqualTo(1);
        }
    }

    @Nested
    class 판매자_목록 {

        @Test
        void 다른_판매자의_프로젝트는_섞이지_않는다() {
            // given
            saveDraft();
            projectRepository.save(Project.createDraft(UUID.randomUUID()));

            // when
            List<Project> projects = projectRepository.findBySeller(sellerId, List.of(), 0, 20);

            // then
            assertThat(projects).hasSize(1);
            assertThat(projects.getFirst().getSellerId()).isEqualTo(sellerId);
        }

        @Test
        void 상태_목록으로_걸러진다() {
            // given
            saveDraft();
            Project pending = saveDraft();
            pending.updateDetail("제목", "https://cdn.example.com/a.jpg", Map.of("text", "본문"));
            pending.submitForReview(true);
            projectRepository.save(pending);

            // when
            List<Project> projects = projectRepository.findBySeller(
                    sellerId, List.of(ProjectStatus.PENDING_REVIEW), 0, 20);

            // then
            assertThat(projects).hasSize(1);
            assertThat(projects.getFirst().getStatus()).isEqualTo(ProjectStatus.PENDING_REVIEW);
        }

        @Test
        void 페이지_크기만큼만_돌려주고_전체_건수는_따로_센다() {
            // given
            saveDraft();
            saveDraft();
            saveDraft();

            // when
            List<Project> firstPage = projectRepository.findBySeller(sellerId, List.of(), 0, 2);

            // then
            assertThat(firstPage).hasSize(2);
            assertThat(projectRepository.countBySeller(sellerId, List.of())).isEqualTo(3L);
        }

        @Test
        void 범위를_벗어난_페이지는_빈_목록이다() {
            // given
            saveDraft();

            // when
            List<Project> outOfRange = projectRepository.findBySeller(sellerId, List.of(), 5, 20);

            // then
            assertThat(outOfRange).isEmpty();
        }
    }
}
