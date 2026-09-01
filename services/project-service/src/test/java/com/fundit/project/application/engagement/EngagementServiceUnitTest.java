package com.fundit.project.application.engagement;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.engagement.OpenNotifyRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EngagementService")
@ExtendWith(MockitoExtension.class)
class EngagementServiceUnitTest {

    @Mock
    private ProjectFollowJpaRepository followJpaRepository;
    @Mock
    private OpenNotifyRequestJpaRepository openNotifyJpaRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private EngagementService engagementService;

    @BeforeEach
    void setUp() {
        CurrentUser member = new CurrentUser(ProjectFixture.OTHER_MEMBER_ID, Role.MEMBER);
        when(currentUserProvider.require()).thenReturn(member);
        when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.OTHER_MEMBER_ID))
                .thenReturn(ProjectFixture.ongoing());
    }

    @Nested
    class 팔로우 {

        @Test
        void 로그인_회원과_프로젝트를_묶어_저장한다() {
            // given & when
            engagementService.follow(ProjectFixture.PUBLIC_ID);

            // then
            verify(followJpaRepository).insertIfAbsent(
                    ProjectFixture.PROJECT_ID, ProjectFixture.OTHER_MEMBER_ID);
        }

        @Test
        void 중복_여부를_따로_조회하지_않는다() {
            // given & when
            engagementService.follow(ProjectFixture.PUBLIC_ID);

            // then — 조회와 INSERT 사이의 경합을 없애려고 중복 판정을 DB에 맡겼다
            verify(followJpaRepository, times(1)).insertIfAbsent(
                    ProjectFixture.PROJECT_ID, ProjectFixture.OTHER_MEMBER_ID);
        }

        @Test
        void 언팔로우는_해당_행만_지운다() {
            // given & when
            engagementService.unfollow(ProjectFixture.PUBLIC_ID);

            // then
            verify(followJpaRepository).deleteByProjectIdAndMemberId(
                    ProjectFixture.PROJECT_ID, ProjectFixture.OTHER_MEMBER_ID);
        }
    }

    @Nested
    class 오픈알림 {

        @Test
        void 로그인_회원과_프로젝트를_묶어_저장한다() {
            // given & when
            engagementService.requestOpenNotify(ProjectFixture.PUBLIC_ID);

            // then
            verify(openNotifyJpaRepository).insertIfAbsent(
                    ProjectFixture.PROJECT_ID, ProjectFixture.OTHER_MEMBER_ID);
        }

        @Test
        void 취소는_해당_행만_지운다() {
            // given & when
            engagementService.cancelOpenNotify(ProjectFixture.PUBLIC_ID);

            // then
            verify(openNotifyJpaRepository).deleteByProjectIdAndMemberId(
                    ProjectFixture.PROJECT_ID, ProjectFixture.OTHER_MEMBER_ID);
        }
    }
}
