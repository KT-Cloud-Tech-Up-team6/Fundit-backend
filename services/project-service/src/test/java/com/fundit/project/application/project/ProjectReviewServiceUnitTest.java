package com.fundit.project.application.project;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.NotificationPort;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectReviewRequestRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.project.ReviewDecision;
import com.fundit.project.domain.project.ReviewRequestStatus;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.engagement.OpenNotifyRequestJpaEntity;
import com.fundit.project.infrastructure.persistence.engagement.OpenNotifyRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaEntity;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProjectReviewService")
@ExtendWith(MockitoExtension.class)
class ProjectReviewServiceUnitTest {

    private static final Instant START_AT = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-10-10T00:00:00Z");

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

    private CurrentUser admin;

    @BeforeEach
    void setUp() {
        admin = new CurrentUser(UUID.fromString("44444444-4444-4444-8444-444444444444"), Role.ADMIN);
        when(currentUserProvider.require()).thenReturn(admin);
    }

    private void givenSavePassthrough() {
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    class 승인 {

        @Test
        void 진행중으로_전환된다() {
            // given
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());
            givenSavePassthrough();

            // when
            Project reviewed = projectReviewService.review(
                    ProjectFixture.PUBLIC_ID, ReviewDecision.APPROVE, null, START_AT, DEADLINE);

            // then
            assertThat(reviewed.getStatus()).isEqualTo(ProjectStatus.ONGOING);
        }

        @Test
        void 검수_이력이_승인으로_갱신된다() {
            // given
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());
            givenSavePassthrough();

            // when
            projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.APPROVE, null, START_AT, DEADLINE);

            // then
            verify(reviewRequestRepository).resolveLatest(eq(ProjectFixture.PROJECT_ID),
                    eq(ReviewRequestStatus.APPROVED), isNull(), eq(admin.id()), any(Instant.class));
        }

        @Test
        void 오픈알림_신청자와_팔로워에게_알림이_발송된다() {
            // given
            UUID notifyMember = UUID.fromString("55555555-5555-4555-8555-555555555555");
            UUID follower = UUID.fromString("66666666-6666-4666-8666-666666666666");
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());
            when(openNotifyRequestJpaRepository.findByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(OpenNotifyRequestJpaEntity.builder().memberId(notifyMember).build()));
            when(projectFollowJpaRepository.findByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(ProjectFollowJpaEntity.builder().memberId(follower).build()));
            givenSavePassthrough();

            // when
            projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.APPROVE, null, START_AT, DEADLINE);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
            verify(notificationPort).notifyProjectOpened(eq(ProjectFixture.PROJECT_ID), captor.capture());
            assertThat(captor.getValue()).containsExactlyInAnyOrder(notifyMember, follower);
        }

        @Test
        void 오픈알림과_팔로우를_모두_한_회원에게는_한_번만_보낸다() {
            // given
            UUID member = UUID.fromString("55555555-5555-4555-8555-555555555555");
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());
            when(openNotifyRequestJpaRepository.findByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(OpenNotifyRequestJpaEntity.builder().memberId(member).build()));
            when(projectFollowJpaRepository.findByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(ProjectFollowJpaEntity.builder().memberId(member).build()));
            givenSavePassthrough();

            // when
            projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.APPROVE, null, START_AT, DEADLINE);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
            verify(notificationPort).notifyProjectOpened(eq(ProjectFixture.PROJECT_ID), captor.capture());
            assertThat(captor.getValue()).containsExactly(member);
        }
    }

    @Nested
    class 반려 {

        @Test
        void 작성중으로_되돌아간다() {
            // given
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());
            givenSavePassthrough();

            // when
            Project reviewed = projectReviewService.review(
                    ProjectFixture.PUBLIC_ID, ReviewDecision.REJECT, "서류 미비", null, null);

            // then
            assertThat(reviewed.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        }

        @Test
        void 반려_사유가_검수_이력에_남는다() {
            // given
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());
            givenSavePassthrough();

            // when
            projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.REJECT, "서류 미비", null, null);

            // then
            verify(reviewRequestRepository).resolveLatest(eq(ProjectFixture.PROJECT_ID),
                    eq(ReviewRequestStatus.REJECTED), eq("서류 미비"), eq(admin.id()), any(Instant.class));
        }

        @Test
        void 판매자에게_반려_알림이_발송된다() {
            // given
            when(accessGuard.findOrThrow(ProjectFixture.PUBLIC_ID)).thenReturn(ProjectFixture.pendingReview());
            givenSavePassthrough();

            // when
            projectReviewService.review(ProjectFixture.PUBLIC_ID, ReviewDecision.REJECT, "서류 미비", null, null);

            // then
            verify(notificationPort).notifyProjectRejected(
                    ProjectFixture.PROJECT_ID, ProjectFixture.SELLER_ID, "서류 미비");
        }
    }
}
