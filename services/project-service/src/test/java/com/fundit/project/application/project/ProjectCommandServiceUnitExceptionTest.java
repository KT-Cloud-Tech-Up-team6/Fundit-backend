package com.fundit.project.application.project;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectReviewRequestRepository;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.category.CategoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.fundit.project.support.BusinessExceptionAssertions.assertBusinessException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProjectCommandService 예외")
@ExtendWith(MockitoExtension.class)
class ProjectCommandServiceUnitExceptionTest {

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

    @Nested
    class 기본정보_수정 {

        @Test
        void 카테고리_조합이_마스터에_없으면_예외가_발생한다() {
            // given
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller))
                    .thenReturn(ProjectFixture.emptyDraft());
            when(categoryJpaRepository.existsByIdCategoryMajorAndIdCategoryMinor("없는분류", "없는상세"))
                    .thenReturn(false);

            // when & then
            assertBusinessException(
                    () -> projectCommandService.updateBasicInfo(ProjectFixture.PUBLIC_ID,
                            BusinessType.GENERAL, "없는분류", "없는상세", 5_000_000L, true),
                    ProjectErrorCode.INVALID_CATEGORY);
        }

        @Test
        void 카테고리_검증에_실패하면_저장하지_않는다() {
            // given
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller))
                    .thenReturn(ProjectFixture.emptyDraft());
            when(categoryJpaRepository.existsByIdCategoryMajorAndIdCategoryMinor(any(), any()))
                    .thenReturn(false);

            // when
            assertBusinessException(
                    () -> projectCommandService.updateBasicInfo(ProjectFixture.PUBLIC_ID,
                            BusinessType.GENERAL, "없는분류", "없는상세", 5_000_000L, true),
                    ProjectErrorCode.INVALID_CATEGORY);

            // then
            verify(projectRepository, never()).save(any(Project.class));
        }
    }

    @Nested
    class 검수_요청 {

        @Test
        void 이미_검수_중이면_중복_요청_예외가_발생한다() {
            // given
            Project pendingReview = ProjectFixture.pendingReview();
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(pendingReview);

            // when & then — 수정 잠금(423)이 아니라 중복 검수요청(409)으로 구분해야 한다
            assertBusinessException(
                    () -> projectCommandService.updateDetail(ProjectFixture.PUBLIC_ID, null, null, null, false),
                    CommonErrorCode.CONFLICT);
        }

        @Test
        void 중복_요청이면_검수_이력을_남기지_않는다() {
            // given
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller))
                    .thenReturn(ProjectFixture.pendingReview());

            // when
            assertBusinessException(
                    () -> projectCommandService.updateDetail(ProjectFixture.PUBLIC_ID, null, null, null, false),
                    CommonErrorCode.CONFLICT);

            // then
            verify(reviewRequestRepository, never()).submit(anyLong(), any(Instant.class));
        }

        @Test
        void 리워드가_없으면_입력값_예외가_발생한다() {
            // given
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.draft());
            when(rewardRepository.existsActiveByProjectId(ProjectFixture.PROJECT_ID)).thenReturn(false);

            // when & then
            assertBusinessException(
                    () -> projectCommandService.updateDetail(ProjectFixture.PUBLIC_ID, null, null, null, false),
                    CommonErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    class 임시저장 {

        @Test
        void 검수_중이면_수정_잠금_예외가_발생한다() {
            // given
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller))
                    .thenReturn(ProjectFixture.pendingReview());

            // when & then
            assertBusinessException(
                    () -> projectCommandService.updateDetail(ProjectFixture.PUBLIC_ID, "바꾼 제목", null, null, true),
                    CommonErrorCode.RESOURCE_LOCKED);
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 진행_중이면_삭제_불가_예외가_발생한다() {
            // given
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.ongoing());

            // when & then
            assertBusinessException(
                    () -> projectCommandService.delete(ProjectFixture.PUBLIC_ID),
                    ProjectErrorCode.PROJECT_NOT_DELETABLE);
        }
    }
}
