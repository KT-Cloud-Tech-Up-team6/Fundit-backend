package com.fundit.project.application.project;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectReviewRequestRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.category.CategoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProjectCommandService")
@ExtendWith(MockitoExtension.class)
class ProjectCommandServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectReviewRequestRepository reviewRequestRepository;
    @Mock
    private RewardRepository rewardRepository;
    @Mock
    private CategoryJpaRepository categoryJpaRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ProjectCommandService projectCommandService;

    private CurrentUser seller;

    @BeforeEach
    void setUp() {
        seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
    }

    private void givenSavePassthrough() {
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    class 생성 {

        @Test
        void 로그인_사용자를_판매자로_하는_DRAFT가_저장된다() {
            // given
            when(currentUserProvider.require()).thenReturn(seller);
            givenSavePassthrough();

            // when
            Project created = projectCommandService.create();

            // then
            assertThat(created.getSellerId()).isEqualTo(ProjectFixture.SELLER_ID);
            assertThat(created.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        }
    }

    @Nested
    class 기본정보_수정 {

        @Test
        void 카테고리가_마스터에_있으면_저장된다() {
            // given
            Project project = ProjectFixture.emptyDraft();
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(project);
            when(categoryJpaRepository.existsByIdCategoryMajorAndIdCategoryMinor("홈·리빙", "인테리어"))
                    .thenReturn(true);
            givenSavePassthrough();

            // when
            Project updated = projectCommandService.updateBasicInfo(ProjectFixture.PUBLIC_ID,
                    BusinessType.GENERAL, "홈·리빙", "인테리어", 5_000_000L, true);

            // then
            assertThat(updated.getGoalAmount()).isEqualTo(5_000_000L);
            assertThat(updated.getCategoryMajor()).isEqualTo("홈·리빙");
        }
    }

    @Nested
    class 상세페이지_임시저장 {

        @Test
        void 상태를_바꾸지_않고_내용만_저장한다() {
            // given
            Project project = ProjectFixture.draft();
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(project);
            givenSavePassthrough();

            // when
            Project saved = projectCommandService.updateDetail(ProjectFixture.PUBLIC_ID,
                    "임시 제목", null, Map.of("text", "본문"), true);

            // then
            assertThat(saved.getStatus()).isEqualTo(ProjectStatus.DRAFT);
            assertThat(saved.getTitle()).isEqualTo("임시 제목");
        }

        @Test
        void 임시저장에서는_리워드_보유_여부를_조회하지_않는다() {
            // given
            Project project = ProjectFixture.draft();
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(project);
            givenSavePassthrough();

            // when
            projectCommandService.updateDetail(ProjectFixture.PUBLIC_ID, "임시 제목", null, null, true);

            // then
            verify(rewardRepository, never()).existsActiveByProjectId(anyLong());
        }

        @Test
        void 임시저장에서는_검수_요청_이력을_남기지_않는다() {
            // given
            Project project = ProjectFixture.draft();
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(project);
            givenSavePassthrough();

            // when
            projectCommandService.updateDetail(ProjectFixture.PUBLIC_ID, "임시 제목", null, null, true);

            // then
            verify(reviewRequestRepository, never()).submit(anyLong(), any(Instant.class));
        }
    }

    @Nested
    class 검수_요청 {

        @Test
        void 필수값이_충족되면_검수_대기로_전환된다() {
            // given
            Project project = ProjectFixture.draft();
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(project);
            when(rewardRepository.existsActiveByProjectId(ProjectFixture.PROJECT_ID)).thenReturn(true);
            givenSavePassthrough();

            // when
            Project saved = projectCommandService.updateDetail(ProjectFixture.PUBLIC_ID, null, null, null, false);

            // then
            assertThat(saved.getStatus()).isEqualTo(ProjectStatus.PENDING_REVIEW);
        }

        @Test
        void 검수_요청_이력이_기록된다() {
            // given
            Project project = ProjectFixture.draft();
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(project);
            when(rewardRepository.existsActiveByProjectId(ProjectFixture.PROJECT_ID)).thenReturn(true);
            givenSavePassthrough();

            // when
            projectCommandService.updateDetail(ProjectFixture.PUBLIC_ID, null, null, null, false);

            // then
            verify(reviewRequestRepository).submit(eq(ProjectFixture.PROJECT_ID), any(Instant.class));
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 삭제_시각을_채워_저장한다() {
            // given
            Project project = ProjectFixture.draft();
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(project);
            givenSavePassthrough();

            // when
            projectCommandService.delete(ProjectFixture.PUBLIC_ID);

            // then
            ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
            verify(projectRepository).save(captor.capture());
            assertThat(captor.getValue().getDeletedAt()).isNotNull();
        }
    }
}
