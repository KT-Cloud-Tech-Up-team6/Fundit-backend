package com.fundit.project.application.reward;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.fixture.RewardFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RewardService")
@ExtendWith(MockitoExtension.class)
class RewardServiceUnitTest {

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
        when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.draft());
    }

    private void givenSavePassthrough() {
        when(rewardRepository.save(any(Reward.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    class 등록 {

        @Test
        void 프로젝트에_묶여_저장된다() {
            // given
            givenSavePassthrough();

            // when
            Reward created = rewardService.create(ProjectFixture.PUBLIC_ID, "가습기 기본형", "설명", 39_000L,
                    false, true, false, "ELECTRONICS", Map.of("모델명", "H-100"));

            // then
            assertThat(created.getProjectId()).isEqualTo(ProjectFixture.PROJECT_ID);
            assertThat(created.getName()).isEqualTo("가습기 기본형");
        }

        @Test
        void 고시_항목은_검증하지_않고_그대로_저장한다() {
            // given
            givenSavePassthrough();

            // when — 고시 필수값 검증은 검수 요청 시점 몫이라 등록은 통과해야 한다
            Reward created = rewardService.create(ProjectFixture.PUBLIC_ID, "이름", null, 1_000L,
                    false, false, false, "ELECTRONICS", null);

            // then
            assertThat(created.getDisclosure()).isNull();
        }
    }

    @Nested
    class 수정 {

        @Test
        void 전달한_값이_반영된다() {
            // given
            when(rewardRepository.findActiveById(RewardFixture.REWARD_ID))
                    .thenReturn(Optional.of(RewardFixture.reward()));
            givenSavePassthrough();

            // when
            Reward updated = rewardService.update(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                    "바뀐 이름", null, 45_000L, null, null, null, null, null);

            // then
            assertThat(updated.getName()).isEqualTo("바뀐 이름");
            assertThat(updated.getPrice()).isEqualTo(45_000L);
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 펀딩_참여가_없으면_삭제_시각이_기록된다() {
            // given
            when(rewardRepository.findActiveById(RewardFixture.REWARD_ID))
                    .thenReturn(Optional.of(RewardFixture.reward()));
            when(fundingPort.hasFundingForReward(RewardFixture.REWARD_ID)).thenReturn(false);
            givenSavePassthrough();

            // when
            rewardService.delete(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID);

            // then
            ArgumentCaptor<Reward> captor = ArgumentCaptor.forClass(Reward.class);
            verify(rewardRepository).save(captor.capture());
            assertThat(captor.getValue().getDeletedAt()).isNotNull();
        }
    }
}
