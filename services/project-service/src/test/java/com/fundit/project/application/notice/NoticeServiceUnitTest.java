package com.fundit.project.application.notice;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.port.NotificationPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaEntity;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaRepository;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeCommentJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeCommentJpaRepository;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("NoticeService")
@ExtendWith(MockitoExtension.class)
class NoticeServiceUnitTest {

    private static final Long NOTICE_ID = 301L;

    @Mock
    private ProjectNoticeJpaRepository noticeJpaRepository;
    @Mock
    private ProjectNoticeCommentJpaRepository commentJpaRepository;
    @Mock
    private ProjectFollowJpaRepository followJpaRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private MemberPort memberPort;
    @Mock
    private NotificationPort notificationPort;

    @InjectMocks
    private NoticeService noticeService;

    private static ProjectNoticeJpaEntity notice(Long id, String type) {
        return ProjectNoticeJpaEntity.builder()
                .id(id)
                .projectId(ProjectFixture.PROJECT_ID)
                .noticeType(type)
                .title("제목")
                .content("내용")
                .build();
    }

    private static ProjectNoticeCommentJpaEntity comment(Long id, UUID memberId) {
        return ProjectNoticeCommentJpaEntity.builder()
                .id(id)
                .noticeId(NOTICE_ID)
                .memberId(memberId)
                .content("댓글")
                .build();
    }

    @Nested
    class 등록 {

        @Test
        void 판매자가_등록하면_저장된다() {
            // given
            CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            ProjectNoticeJpaEntity saved = noticeService.create(
                    ProjectFixture.PUBLIC_ID, "제작과정", "1차 생산 완료", "본문");

            // then
            assertThat(saved.getNoticeType()).isEqualTo("제작과정");
            assertThat(saved.getProjectId()).isEqualTo(ProjectFixture.PROJECT_ID);
        }

        @Test
        void 팔로워_전원에게_알림이_발송된다() {
            // given
            UUID follower = UUID.fromString("77777777-7777-4777-8777-777777777777");
            CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.save(any())).thenReturn(notice(NOTICE_ID, "제작과정"));
            when(followJpaRepository.findByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(ProjectFollowJpaEntity.builder().memberId(follower).build()));

            // when
            noticeService.create(ProjectFixture.PUBLIC_ID, "제작과정", "제목", "본문");

            // then
            verify(notificationPort).notifyNoticePublished(
                    ProjectFixture.PROJECT_ID, NOTICE_ID, List.of(follower));
        }
    }

    @Nested
    class 목록_조회 {

        @Test
        void 유형을_주면_해당_유형만_조회한다() {
            // given
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.findByProjectIdAndNoticeTypeOrderByCreatedAtDesc(
                    ProjectFixture.PROJECT_ID, "이벤트")).thenReturn(List.of(notice(NOTICE_ID, "이벤트")));
            when(commentJpaRepository.findByNoticeIdAndDeletedAtIsNullOrderByCreatedAtDesc(NOTICE_ID))
                    .thenReturn(List.of());

            // when
            var summaries = noticeService.list(ProjectFixture.PUBLIC_ID, "이벤트", null);

            // then
            assertThat(summaries).hasSize(1);
            verify(noticeJpaRepository).findByProjectIdAndNoticeTypeOrderByCreatedAtDesc(
                    ProjectFixture.PROJECT_ID, "이벤트");
        }

        @Test
        void 댓글_수가_함께_계산된다() {
            // given
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.findByProjectIdOrderByCreatedAtDesc(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(notice(NOTICE_ID, "이벤트")));
            when(commentJpaRepository.findByNoticeIdAndDeletedAtIsNullOrderByCreatedAtDesc(NOTICE_ID))
                    .thenReturn(List.of(comment(1L, ProjectFixture.OTHER_MEMBER_ID),
                            comment(2L, ProjectFixture.OTHER_MEMBER_ID)));

            // when
            var summaries = noticeService.list(ProjectFixture.PUBLIC_ID, null, null);

            // then
            assertThat(summaries.getFirst().commentCount()).isEqualTo(2);
        }

        @Test
        void 인기순이면_댓글이_많은_순으로_정렬된다() {
            // given
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.findByProjectIdOrderByCreatedAtDesc(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(notice(301L, "이벤트"), notice(302L, "이벤트")));
            when(commentJpaRepository.findByNoticeIdAndDeletedAtIsNullOrderByCreatedAtDesc(301L))
                    .thenReturn(List.of());
            when(commentJpaRepository.findByNoticeIdAndDeletedAtIsNullOrderByCreatedAtDesc(302L))
                    .thenReturn(List.of(comment(1L, ProjectFixture.OTHER_MEMBER_ID)));

            // when
            var summaries = noticeService.list(ProjectFixture.PUBLIC_ID, null, "POPULAR");

            // then
            assertThat(summaries.getFirst().notice().getId()).isEqualTo(302L);
        }
    }

    @Nested
    class 댓글 {

        @Test
        void 로그인_회원이면_작성할_수_있다() {
            // given
            CurrentUser member = new CurrentUser(ProjectFixture.OTHER_MEMBER_ID, Role.MEMBER);
            when(currentUserProvider.require()).thenReturn(member);
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.OTHER_MEMBER_ID))
                    .thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.findById(NOTICE_ID)).thenReturn(Optional.of(notice(NOTICE_ID, "이벤트")));
            when(commentJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            var saved = noticeService.addComment(ProjectFixture.PUBLIC_ID, NOTICE_ID, "응원합니다");

            // then
            assertThat(saved.getContent()).isEqualTo("응원합니다");
            assertThat(saved.getMemberId()).isEqualTo(ProjectFixture.OTHER_MEMBER_ID);
        }

        @Test
        void 목록에_작성자_닉네임이_붙는다() {
            // given
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.findById(NOTICE_ID)).thenReturn(Optional.of(notice(NOTICE_ID, "이벤트")));
            when(commentJpaRepository.findByNoticeIdAndDeletedAtIsNullOrderByCreatedAtDesc(NOTICE_ID))
                    .thenReturn(List.of(comment(1L, ProjectFixture.OTHER_MEMBER_ID)));
            when(memberPort.findNicknames(List.of(ProjectFixture.OTHER_MEMBER_ID)))
                    .thenReturn(Map.of(ProjectFixture.OTHER_MEMBER_ID, "펀딩왕"));

            // when
            var comments = noticeService.listComments(ProjectFixture.PUBLIC_ID, NOTICE_ID);

            // then
            assertThat(comments.getFirst().nickname()).isEqualTo("펀딩왕");
        }

        @Test
        void 닉네임을_받아오지_못해도_댓글은_돌려준다() {
            // given
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.findById(NOTICE_ID)).thenReturn(Optional.of(notice(NOTICE_ID, "이벤트")));
            when(commentJpaRepository.findByNoticeIdAndDeletedAtIsNullOrderByCreatedAtDesc(NOTICE_ID))
                    .thenReturn(List.of(comment(1L, ProjectFixture.OTHER_MEMBER_ID)));
            when(memberPort.findNicknames(List.of(ProjectFixture.OTHER_MEMBER_ID))).thenReturn(Map.of());

            // when
            var comments = noticeService.listComments(ProjectFixture.PUBLIC_ID, NOTICE_ID);

            // then
            assertThat(comments).hasSize(1);
            assertThat(comments.getFirst().nickname()).isNull();
        }
    }
}
