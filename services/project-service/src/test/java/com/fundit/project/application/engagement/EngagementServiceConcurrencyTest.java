package com.fundit.project.application.engagement;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.support.ConcurrentRunner;
import com.fundit.project.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 팔로우와 오픈알림신청은 (project_id, member_id) 유니크라 중복 요청도 성공으로 처리한다.
 * 그런데 "있는지 보고 없으면 넣는" 순서라, 같은 회원의 요청이 동시에 들어오면
 * 둘 다 조회를 통과해 유니크 위반이 난다 — 멱등이라면 그 경우에도 실패하면 안 된다.
 */
@DisplayName("EngagementService 동시성")
@Sql("/sql/insert-categories.sql")
class EngagementServiceConcurrencyTest extends IntegrationTestSupport {

    private static final int THREAD_COUNT = 8;

    @Autowired
    private EngagementService engagementService;
    @Autowired
    private ProjectRepository projectRepository;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private UUID projectPublicId;
    private Long projectId;

    @BeforeEach
    void setUp() {
        UUID memberId = UUID.randomUUID();
        CurrentUser member = new CurrentUser(memberId, Role.MEMBER);
        when(currentUserProvider.require()).thenReturn(member);
        when(currentUserProvider.find()).thenReturn(Optional.of(member));

        UUID sellerId = UUID.randomUUID();
        Project project = projectRepository.save(Project.createDraft(sellerId));
        projectPublicId = project.getPublicId();
        projectId = project.getId();
        // 팔로우·오픈알림은 공개된 프로젝트에만 걸 수 있어 상태를 진행중으로 맞춰 둔다
        jdbcTemplate.update("UPDATE projects SET status = 'ONGOING' WHERE id = ?", projectId);
    }

    @Nested
    class 팔로우 {

        @Test
        void 동시에_눌러도_모두_성공한다() throws Exception {
            // given & when
            ConcurrentRunner.Result result = ConcurrentRunner.runAll(THREAD_COUNT,
                    () -> engagementService.follow(projectPublicId));

            // then
            assertThat(result.failures()).isEmpty();
            assertThat(result.successCount()).isEqualTo(THREAD_COUNT);
        }

        @Test
        void 동시에_눌러도_한_건만_쌓인다() throws Exception {
            // given & when
            ConcurrentRunner.runAll(THREAD_COUNT, () -> engagementService.follow(projectPublicId));

            // then
            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM project_follows WHERE project_id = ?", Integer.class, projectId);
            assertThat(rows).isEqualTo(1);
        }
    }

    @Nested
    class 오픈알림 {

        @Test
        void 동시에_신청해도_모두_성공한다() throws Exception {
            // given & when
            ConcurrentRunner.Result result = ConcurrentRunner.runAll(THREAD_COUNT,
                    () -> engagementService.requestOpenNotify(projectPublicId));

            // then
            assertThat(result.failures()).isEmpty();
            assertThat(result.successCount()).isEqualTo(THREAD_COUNT);
        }

        @Test
        void 동시에_신청해도_한_건만_쌓인다() throws Exception {
            // given & when
            ConcurrentRunner.runAll(THREAD_COUNT, () -> engagementService.requestOpenNotify(projectPublicId));

            // then
            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM project_open_notify_requests WHERE project_id = ?",
                    Integer.class, projectId);
            assertThat(rows).isEqualTo(1);
        }
    }
}
