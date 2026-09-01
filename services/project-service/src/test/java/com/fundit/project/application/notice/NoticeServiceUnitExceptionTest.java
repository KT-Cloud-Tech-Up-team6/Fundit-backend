package com.fundit.project.application.notice;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.port.NotificationPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaRepository;
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

import java.util.Optional;

import static com.fundit.project.support.BusinessExceptionAssertions.assertBusinessException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("NoticeService 예외")
@ExtendWith(MockitoExtension.class)
class NoticeServiceUnitExceptionTest {

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

    @Nested
    class 등록 {

        @Test
        void 지원하지_않는_유형이면_예외가_발생한다() {
            // given
            CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.ongoing());

            // when & then
            assertBusinessException(
                    () -> noticeService.create(ProjectFixture.PUBLIC_ID, "없는유형", "제목", "본문"),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 유형_검증에_실패하면_저장하지_않는다() {
            // given
            CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
            when(currentUserProvider.require()).thenReturn(seller);
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.ongoing());

            // when
            assertBusinessException(
                    () -> noticeService.create(ProjectFixture.PUBLIC_ID, "없는유형", "제목", "본문"),
                    CommonErrorCode.INVALID_INPUT);

            // then
            verify(noticeJpaRepository, never()).save(any());
            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    class 댓글_작성 {

        @Test
        void 최대_길이를_넘으면_예외가_발생한다() {
            // given
            CurrentUser member = new CurrentUser(ProjectFixture.OTHER_MEMBER_ID, Role.MEMBER);
            when(currentUserProvider.require()).thenReturn(member);
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.OTHER_MEMBER_ID))
                    .thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.findById(NOTICE_ID)).thenReturn(Optional.of(
                    ProjectNoticeJpaEntity.builder().id(NOTICE_ID).projectId(ProjectFixture.PROJECT_ID).build()));

            // when & then
            assertBusinessException(
                    () -> noticeService.addComment(ProjectFixture.PUBLIC_ID, NOTICE_ID, "가".repeat(501)),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 내용이_없으면_예외가_발생한다() {
            // given
            CurrentUser member = new CurrentUser(ProjectFixture.OTHER_MEMBER_ID, Role.MEMBER);
            when(currentUserProvider.require()).thenReturn(member);
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.OTHER_MEMBER_ID))
                    .thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.findById(NOTICE_ID)).thenReturn(Optional.of(
                    ProjectNoticeJpaEntity.builder().id(NOTICE_ID).projectId(ProjectFixture.PROJECT_ID).build()));

            // when & then
            assertBusinessException(
                    () -> noticeService.addComment(ProjectFixture.PUBLIC_ID, NOTICE_ID, null),
                    CommonErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    class 새소식_확인 {

        @Test
        void 없는_새소식이면_없음_예외가_발생한다() {
            // given
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.findById(NOTICE_ID)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(
                    () -> noticeService.listComments(ProjectFixture.PUBLIC_ID, NOTICE_ID),
                    CommonErrorCode.NOT_FOUND);
        }

        @Test
        void 다른_프로젝트의_새소식이면_없음_예외가_발생한다() {
            // given
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
            when(noticeJpaRepository.findById(NOTICE_ID)).thenReturn(Optional.of(
                    ProjectNoticeJpaEntity.builder().id(NOTICE_ID).projectId(999L).build()));

            // when & then
            assertBusinessException(
                    () -> noticeService.listComments(ProjectFixture.PUBLIC_ID, NOTICE_ID),
                    CommonErrorCode.NOT_FOUND);
        }
    }
}
