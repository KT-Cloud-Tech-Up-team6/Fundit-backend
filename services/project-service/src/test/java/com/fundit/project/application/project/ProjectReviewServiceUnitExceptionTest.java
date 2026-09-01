package com.fundit.project.application.project;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.NotificationPort;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectReviewRequestRepository;
import com.fundit.project.domain.project.ReviewDecision;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.engagement.OpenNotifyRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static com.fundit.project.support.BusinessExceptionAssertions.assertBusinessException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ProjectReviewService 예외")
@ExtendWith(MockitoExtension.class)
class ProjectReviewServiceUnitExceptionTest {

    private static final Instant START_AT = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-10-10T00:00:00Z");
    private static final CurrentUser ADMIN =
            new CurrentUser(UUID.fromString("44444444-4444-4444-8444-444444444444"), Role.ADMIN);

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectReviewRequestRepository reviewRequestRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private NotificationPort notificationPort;
    @Mock
    private OpenNotifyRequestJpaRepository openNotifyRequestJpaRepository;
    @Mock
    private ProjectFollowJpaRepository projectFollowJpaRepository;

    @InjectMocks
    private ProjectReviewService projectReviewService;

    @Nested
    class 권한 {

        @Test
        void 일반_회원이_호출하면_권한_예외가_발생한다() {
            // given
            when(currentUserProvider.require())
                    .thenReturn(new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER));

            // when & then
            assertBusinessException(
                    () -> projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.APPROVE,
                            null, START_AT, DEADLINE),
                    CommonErrorCode.FORBIDDEN);
        }

        @Test
        void 권한이_없으면_프로젝트를_조회조차_하지_않는다() {
            // given
            when(currentUserProvider.require())
                    .thenReturn(new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER));

            // when
            assertBusinessException(
                    () -> projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.APPROVE,
                            null, START_AT, DEADLINE),
                    CommonErrorCode.FORBIDDEN);

            // then
            verifyNoInteractions(accessGuard);
        }
    }

    @Nested
    class 상태 {

        @Test
        void 검수_대기가_아니면_예외가_발생한다() {
            // given
            when(currentUserProvider.require()).thenReturn(ADMIN);
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.draft());

            // when & then
            assertBusinessException(
                    () -> projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.APPROVE,
                            null, START_AT, DEADLINE),
                    ProjectErrorCode.PROJECT_REVIEW_NOT_PENDING);
        }

        @Test
        void 상태_전이에_실패하면_저장하지_않는다() {
            // given
            when(currentUserProvider.require()).thenReturn(ADMIN);
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.ongoing());

            // when
            assertBusinessException(
                    () -> projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.REJECT,
                            "사유", null, null),
                    ProjectErrorCode.PROJECT_REVIEW_NOT_PENDING);

            // then
            verify(projectRepository, never()).save(any(Project.class));
        }
    }

    @Nested
    class 반려_사유 {

        @Test
        void 사유가_없으면_예외가_발생한다() {
            // given
            when(currentUserProvider.require()).thenReturn(ADMIN);
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());

            // when & then
            assertBusinessException(
                    () -> projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.REJECT,
                            null, null, null),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 사유가_공백뿐이면_예외가_발생한다() {
            // given
            when(currentUserProvider.require()).thenReturn(ADMIN);
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());

            // when & then
            assertBusinessException(
                    () -> projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.REJECT,
                            "   ", null, null),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 사유_누락으로_실패하면_알림을_보내지_않는다() {
            // given
            when(currentUserProvider.require()).thenReturn(ADMIN);
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());

            // when
            assertBusinessException(
                    () -> projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.REJECT,
                            null, null, null),
                    CommonErrorCode.INVALID_INPUT);

            // then
            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class 펀딩_일정 {

        @Test
        void 승인하면서_일정을_주지_않으면_예외가_발생한다() {
            // given
            when(currentUserProvider.require()).thenReturn(ADMIN);
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());

            // when & then
            assertBusinessException(
                    () -> projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.APPROVE,
                            null, null, null),
                    CommonErrorCode.INVALID_INPUT);
        }
    }
}
