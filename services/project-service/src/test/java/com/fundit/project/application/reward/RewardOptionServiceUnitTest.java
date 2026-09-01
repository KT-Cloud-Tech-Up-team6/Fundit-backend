package com.fundit.project.application.reward;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.port.InventoryPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.reward.RewardOption;
import com.fundit.project.domain.reward.RewardOptionRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RewardOptionService")
@ExtendWith(MockitoExtension.class)
class RewardOptionServiceUnitTest {

    @Mock
    private RewardRepository rewardRepository;
    @Mock
    private RewardOptionRepository rewardOptionRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private InventoryPort inventoryPort;
    @Mock
    private FundingPort fundingPort;

    @InjectMocks
    private RewardOptionService rewardOptionService;

    @BeforeEach
    void setUp() {
        CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
        when(currentUserProvider.require()).thenReturn(seller);
        when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.draft());
        when(rewardRepository.findActiveById(RewardFixture.REWARD_ID))
                .thenReturn(Optional.of(RewardFixture.reward()));
    }

    private void givenSavePassthrough() {
        when(rewardOptionRepository.save(any(RewardOption.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** 신규 옵션은 저장 전까지 id가 없다. 재고 초기화는 채번된 id로 나가야 하므로 저장 결과를 흉내 낸다. */
    private void givenSaveAssignsId() {
        when(rewardOptionRepository.save(any(RewardOption.class)))
                .thenAnswer(invocation -> {
                    RewardOption unsaved = invocation.getArgument(0);
                    return RewardFixture.optionBase()
                            .rewardId(unsaved.getRewardId())
                            .optionName(unsaved.getOptionName())
                            .sku(unsaved.getSku())
                            .build();
                });
    }

    @Nested
    class 등록 {

        @Test
        void 리워드에_묶여_저장된다() {
            // given
            when(rewardOptionRepository.existsBySku(RewardFixture.SKU)).thenReturn(false);
            givenSaveAssignsId();

            // when
            RewardOption created = rewardOptionService.create(
                    ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID, "화이트", RewardFixture.SKU, 10);

            // then
            assertThat(created.getRewardId()).isEqualTo(RewardFixture.REWARD_ID);
            assertThat(created.getSku()).isEqualTo(RewardFixture.SKU);
        }

        @Test
        void 재고_초기화가_order_service에_위임된다() {
            // given
            when(rewardOptionRepository.existsBySku(RewardFixture.SKU)).thenReturn(false);
            givenSaveAssignsId();

            // when
            rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                    "화이트", RewardFixture.SKU, 10);

            // then
            verify(inventoryPort).initialize(RewardFixture.OPTION_ID, RewardFixture.SKU, 10);
        }

        @Test
        void 초기_재고가_0이어도_등록된다() {
            // given
            when(rewardOptionRepository.existsBySku(RewardFixture.SKU)).thenReturn(false);
            givenSaveAssignsId();

            // when
            rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                    "화이트", RewardFixture.SKU, 0);

            // then
            verify(inventoryPort).initialize(RewardFixture.OPTION_ID, RewardFixture.SKU, 0);
        }
    }

    @Nested
    class 옵션명_수정 {

        @Test
        void 새_이름으로_저장된다() {
            // given
            when(rewardOptionRepository.findActiveById(RewardFixture.OPTION_ID))
                    .thenReturn(Optional.of(RewardFixture.option()));
            givenSavePassthrough();

            // when
            RewardOption updated = rewardOptionService.rename(ProjectFixture.PUBLIC_ID,
                    RewardFixture.REWARD_ID, RewardFixture.OPTION_ID, "블랙");

            // then
            assertThat(updated.getOptionName()).isEqualTo("블랙");
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 삭제_시각이_기록된다() {
            // given
            when(rewardOptionRepository.findActiveById(RewardFixture.OPTION_ID))
                    .thenReturn(Optional.of(RewardFixture.option()));
            when(fundingPort.hasFundingForOption(RewardFixture.OPTION_ID)).thenReturn(false);
            givenSavePassthrough();

            // when
            rewardOptionService.delete(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID, RewardFixture.OPTION_ID);

            // then
            ArgumentCaptor<RewardOption> captor = ArgumentCaptor.forClass(RewardOption.class);
            verify(rewardOptionRepository).save(captor.capture());
            assertThat(captor.getValue().getDeletedAt()).isNotNull();
        }

        @Test
        void 재고_비활성화가_order_service에_위임된다() {
            // given
            when(rewardOptionRepository.findActiveById(RewardFixture.OPTION_ID))
                    .thenReturn(Optional.of(RewardFixture.option()));
            when(fundingPort.hasFundingForOption(RewardFixture.OPTION_ID)).thenReturn(false);
            givenSavePassthrough();

            // when
            rewardOptionService.delete(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID, RewardFixture.OPTION_ID);

            // then
            verify(inventoryPort).deactivate(RewardFixture.OPTION_ID);
        }
    }
}
