package com.fundit.project.application.project;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.fixture.ProjectFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.fundit.project.support.BusinessExceptionAssertions.assertBusinessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("ProjectAccessGuard")
@ExtendWith(MockitoExtension.class)
class ProjectAccessGuardUnitTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectAccessGuard accessGuard;

    @Nested
    class 소유자_전용_조회 {

        @Test
        void 판매자_본인이면_통과한다() {
            // given
            Project project = ProjectFixture.draft();
            CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
            when(projectRepository.findByPublicId(ProjectFixture.PUBLIC_ID)).thenReturn(Optional.of(project));

            // when
            Project found = accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller);

            // then
            assertThat(found).isSameAs(project);
        }

        @Test
        void 다른_회원이면_권한_예외가_발생한다() {
            // given
            CurrentUser other = new CurrentUser(ProjectFixture.OTHER_MEMBER_ID, Role.MEMBER);
            when(projectRepository.findByPublicId(ProjectFixture.PUBLIC_ID))
                    .thenReturn(Optional.of(ProjectFixture.draft()));

            // when & then
            assertBusinessException(
                    () -> accessGuard.findOwned(ProjectFixture.PUBLIC_ID, other),
                    CommonErrorCode.FORBIDDEN);
        }

        @Test
        void 존재하지_않으면_없음_예외가_발생한다() {
            // given
            CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
            when(projectRepository.findByPublicId(ProjectFixture.PUBLIC_ID)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(
                    () -> accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller),
                    CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    class 공개_조회 {

        @Test
        void 진행중이면_비로그인도_조회할_수_있다() {
            // given
            Project project = ProjectFixture.ongoing();
            when(projectRepository.findByPublicId(ProjectFixture.PUBLIC_ID)).thenReturn(Optional.of(project));

            // when
            Project found = accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null);

            // then
            assertThat(found).isSameAs(project);
        }

        @Test
        void 비공개_프로젝트를_남이_보면_권한이_아니라_없음으로_응답한다() {
            // given
            when(projectRepository.findByPublicId(ProjectFixture.PUBLIC_ID))
                    .thenReturn(Optional.of(ProjectFixture.draft()));

            // when & then — 403을 주면 "그 자리에 무언가 있다"는 사실이 새어나간다
            assertBusinessException(
                    () -> accessGuard.findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.OTHER_MEMBER_ID),
                    CommonErrorCode.NOT_FOUND);
        }

        @Test
        void 비공개여도_소유자면_조회할_수_있다() {
            // given
            Project project = ProjectFixture.pendingReview();
            when(projectRepository.findByPublicId(ProjectFixture.PUBLIC_ID)).thenReturn(Optional.of(project));

            // when
            Project found = accessGuard.findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.SELLER_ID);

            // then
            assertThat(found).isSameAs(project);
        }
    }
}
