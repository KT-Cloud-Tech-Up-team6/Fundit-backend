package com.fundit.project.application.project;

import com.fundit.project.application.funding.FundingStatsReader;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.fixture.RewardFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProjectQueryService")
@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private RewardRepository rewardRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private FundingStatsReader fundingStatsReader;

    @InjectMocks
    private ProjectQueryService projectQueryService;

    @Nested
    class 판매자_목록_조회 {

        @Test
        void 본인_프로젝트만_조회하도록_판매자_식별자를_넘긴다() {
            // given
            CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
            when(currentUserProvider.require()).thenReturn(seller);
            when(projectRepository.findBySeller(ProjectFixture.SELLER_ID, List.of(), 0, 20))
                    .thenReturn(List.of(ProjectFixture.ongoing()));
            when(projectRepository.countBySeller(ProjectFixture.SELLER_ID, List.of())).thenReturn(1L);

            // when
            var result = projectQueryService.listForSeller(null, 0, 20);

            // then
            assertThat(result.projects()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1L);
            verify(projectRepository).findBySeller(ProjectFixture.SELLER_ID, List.of(), 0, 20);
        }

        @Test
        void 준비중_탭은_작성중과_검수중을_함께_조회한다() {
            // given
            CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
            List<ProjectStatus> preparing = List.of(ProjectStatus.DRAFT, ProjectStatus.PENDING_REVIEW);
            when(currentUserProvider.require()).thenReturn(seller);
            when(projectRepository.findBySeller(ProjectFixture.SELLER_ID, preparing, 0, 20))
                    .thenReturn(List.of());
            when(projectRepository.countBySeller(ProjectFixture.SELLER_ID, preparing)).thenReturn(0L);

            // when
            var result = projectQueryService.listForSeller("PREPARING", 0, 20);

            // then
            assertThat(result.projects()).isEmpty();
            verify(projectRepository).findBySeller(ProjectFixture.SELLER_ID, preparing, 0, 20);
        }
    }

    @Nested
    class 상세_조회 {

        @Test
        void 집계값이_병합된다() {
            // given
            Project project = ProjectFixture.ongoing();
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(project);
            when(fundingStatsReader.read(ProjectFixture.PROJECT_ID))
                    .thenReturn(new FundingPort.FundingStats(3_200_000L, 128, List.of()));
            when(rewardRepository.findActiveByProjectId(ProjectFixture.PROJECT_ID)).thenReturn(List.of());

            // when
            var detail = projectQueryService.findDetail(ProjectFixture.PUBLIC_ID);

            // then
            assertThat(detail.stats().currentAmount()).isEqualTo(3_200_000L);
            assertThat(detail.stats().participantCount()).isEqualTo(128);
        }

        @Test
        void 리워드_중_하나라도_단순변심_환불불가면_요약값이_참이_된다() {
            // given
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
            when(fundingStatsReader.read(ProjectFixture.PROJECT_ID))
                    .thenReturn(FundingPort.FundingStats.empty());
            when(rewardRepository.findActiveByProjectId(ProjectFixture.PROJECT_ID)).thenReturn(List.of(
                    RewardFixture.base().simpleRefundDisabled(false).build(),
                    RewardFixture.base().id(502L).simpleRefundDisabled(true).build()));

            // when
            var detail = projectQueryService.findDetail(ProjectFixture.PUBLIC_ID);

            // then
            assertThat(detail.simpleRefundDisabled()).isTrue();
        }

        @Test
        void 리워드가_모두_환불_가능하면_요약값이_거짓이다() {
            // given
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
            when(fundingStatsReader.read(ProjectFixture.PROJECT_ID))
                    .thenReturn(FundingPort.FundingStats.empty());
            when(rewardRepository.findActiveByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(RewardFixture.reward()));

            // when
            var detail = projectQueryService.findDetail(ProjectFixture.PUBLIC_ID);

            // then
            assertThat(detail.simpleRefundDisabled()).isFalse();
        }

        @Test
        void 로그인_사용자면_조회자_식별자가_노출_판정에_전달된다() {
            // given
            CurrentUser viewer = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
            when(currentUserProvider.find()).thenReturn(Optional.of(viewer));
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.SELLER_ID))
                    .thenReturn(ProjectFixture.draft());
            when(fundingStatsReader.read(ProjectFixture.PROJECT_ID))
                    .thenReturn(FundingPort.FundingStats.empty());
            when(rewardRepository.findActiveByProjectId(ProjectFixture.PROJECT_ID)).thenReturn(List.of());

            // when
            projectQueryService.findDetail(ProjectFixture.PUBLIC_ID);

            // then
            verify(accessGuard).findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.SELLER_ID);
        }
    }
}
