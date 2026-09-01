package com.fundit.project.application.reward;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.fixture.RewardFixture;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.when;

@DisplayName("RewardService 예외")
@ExtendWith(MockitoExtension.class)
class RewardServiceUnitExceptionTest {

    @Mock
    private RewardRepository rewardRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private FundingPort fundingPort;

    @InjectMocks
    private RewardService rewardService;

    private CurrentUser seller;

    @BeforeEach
    void setUp() {
        seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
        when(currentUserProvider.require()).thenReturn(seller);
    }

    @Nested
    class 검수_중_잠금 {

        @Test
        void 리워드를_등록하려_하면_잠김_예외가_발생한다() {
            // given
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller))
                    .thenReturn(ProjectFixture.pendingReview());

            // when & then
            assertBusinessException(
                    () -> rewardService.create(ProjectFixture.PUBLIC_ID, "이름", null, 1_000L,
                            false, false, false, null, null),
                    CommonErrorCode.RESOURCE_LOCKED);
        }

        @Test
        void 리워드를_삭제하려_하면_잠김_예외가_발생한다() {
            // given
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller))
                    .thenReturn(ProjectFixture.pendingReview());

            // when & then
            assertBusinessException(
                    () -> rewardService.delete(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID),
                    CommonErrorCode.RESOURCE_LOCKED);
        }
    }

    @Nested
    class 리워드_조회 {

        @Test
        void 존재하지_않으면_없음_예외가_발생한다() {
            // given
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.draft());
            when(rewardRepository.findActiveById(RewardFixture.REWARD_ID)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(
                    () -> rewardService.delete(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID),
                    CommonErrorCode.NOT_FOUND);
        }

        @Test
        void 다른_프로젝트의_리워드면_없음_예외가_발생한다() {
            // given
            Reward otherProjectReward = RewardFixture.base().projectId(999L).build();
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.draft());
            when(rewardRepository.findActiveById(RewardFixture.REWARD_ID))
                    .thenReturn(Optional.of(otherProjectReward));

            // when & then — 경로의 projectId와 리워드 소유 관계가 어긋나면 접근을 막는다
            assertBusinessException(
                    () -> rewardService.update(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            "이름", null, null, null, null, null, null, null),
                    CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 펀딩_참여_이력이_있으면_예외가_발생한다() {
            // given
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.draft());
            when(rewardRepository.findActiveById(RewardFixture.REWARD_ID))
                    .thenReturn(Optional.of(RewardFixture.reward()));
            when(fundingPort.hasFundingForReward(RewardFixture.REWARD_ID)).thenReturn(true);

            // when & then
            assertBusinessException(
                    () -> rewardService.delete(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID),
                    ProjectErrorCode.REWARD_HAS_ACTIVE_FUNDING);
        }

        @Test
        void 펀딩_참여가_있으면_저장하지_않는다() {
            // given
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.draft());
            when(rewardRepository.findActiveById(RewardFixture.REWARD_ID))
                    .thenReturn(Optional.of(RewardFixture.reward()));
            when(fundingPort.hasFundingForReward(RewardFixture.REWARD_ID)).thenReturn(true);

            // when
            assertBusinessException(
                    () -> rewardService.delete(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID),
                    ProjectErrorCode.REWARD_HAS_ACTIVE_FUNDING);

            // then
            verify(rewardRepository, never()).save(any(Reward.class));
        }
    }
}
