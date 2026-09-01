package com.fundit.project.application.community;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.port.NotificationPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaRepository;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.fundit.project.support.BusinessExceptionAssertions.assertBusinessException;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("CommunityService 예외")
@ExtendWith(MockitoExtension.class)
class CommunityServiceUnitExceptionTest {

    private static final Long POST_ID = 401L;

    @Mock
    private CommunityPostJpaRepository postJpaRepository;
    @Mock
    private CommunityAnswerJpaRepository answerJpaRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private MemberPort memberPort;
    @Mock
    private NotificationPort notificationPort;

    @InjectMocks
    private CommunityService communityService;

    @BeforeEach
    void setUp() {
        CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
        when(currentUserProvider.require()).thenReturn(seller);
        when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.ongoing());
    }

    @Test
    void 없는_게시글에_답변하면_없음_예외가_발생한다() {
        // given
        when(postJpaRepository.findById(POST_ID)).thenReturn(Optional.empty());

        // when & then
        assertBusinessException(
                () -> communityService.upsertAnswer(ProjectFixture.PUBLIC_ID, POST_ID, "답변"),
                CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 다른_프로젝트의_게시글이면_없음_예외가_발생한다() {
        // given
        when(postJpaRepository.findById(POST_ID)).thenReturn(Optional.of(
                CommunityPostJpaEntity.builder().id(POST_ID).projectId(999L).build()));

        // when & then
        assertBusinessException(
                () -> communityService.upsertAnswer(ProjectFixture.PUBLIC_ID, POST_ID, "답변"),
                CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 답변에_실패하면_알림을_보내지_않는다() {
        // given
        when(postJpaRepository.findById(POST_ID)).thenReturn(Optional.empty());

        // when
        assertBusinessException(
                () -> communityService.upsertAnswer(ProjectFixture.PUBLIC_ID, POST_ID, "답변"),
                CommonErrorCode.NOT_FOUND);

        // then
        verifyNoInteractions(notificationPort);
    }
}
