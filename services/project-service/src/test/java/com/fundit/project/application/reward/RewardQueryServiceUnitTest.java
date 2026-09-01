package com.fundit.project.application.reward;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.InventoryPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.reward.RewardOptionRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("RewardQueryService")
@ExtendWith(MockitoExtension.class)
class RewardQueryServiceUnitTest {

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

    @InjectMocks
    private RewardQueryService rewardQueryService;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.find()).thenReturn(Optional.empty());
        when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
    }

    @Nested
    class 재고_병합 {

        @Test
        void order_service_재고가_옵션에_붙는다() {
            // given
            when(rewardRepository.findActiveByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(RewardFixture.reward()));
            when(rewardOptionRepository.findActiveByRewardIds(List.of(RewardFixture.REWARD_ID)))
                    .thenReturn(List.of(RewardFixture.option()));
            when(inventoryPort.findAvailableStocks(List.of(RewardFixture.OPTION_ID)))
                    .thenReturn(Map.of(RewardFixture.OPTION_ID, 7));

            // when
            var rewards = rewardQueryService.listWithStock(ProjectFixture.PUBLIC_ID);

            // then
            assertThat(rewards).hasSize(1);
            assertThat(rewards.getFirst().options().getFirst().availableStock()).isEqualTo(7);
            assertThat(rewards.getFirst().options().getFirst().soldOut()).isFalse();
        }

        @Test
        void 재고가_0이면_품절로_표시된다() {
            // given
            when(rewardRepository.findActiveByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(RewardFixture.reward()));
            when(rewardOptionRepository.findActiveByRewardIds(List.of(RewardFixture.REWARD_ID)))
                    .thenReturn(List.of(RewardFixture.option()));
            when(inventoryPort.findAvailableStocks(List.of(RewardFixture.OPTION_ID)))
                    .thenReturn(Map.of(RewardFixture.OPTION_ID, 0));

            // when
            var rewards = rewardQueryService.listWithStock(ProjectFixture.PUBLIC_ID);

            // then
            assertThat(rewards.getFirst().options().getFirst().soldOut()).isTrue();
        }

        @Test
        void 무제한_리워드는_재고를_표시하지_않는다() {
            // given
            when(rewardRepository.findActiveByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(RewardFixture.unlimitedReward()));
            when(rewardOptionRepository.findActiveByRewardIds(List.of(RewardFixture.REWARD_ID)))
                    .thenReturn(List.of(RewardFixture.option()));
            when(inventoryPort.findAvailableStocks(List.of(RewardFixture.OPTION_ID))).thenReturn(Map.of());

            // when
            var rewards = rewardQueryService.listWithStock(ProjectFixture.PUBLIC_ID);

            // then
            var optionStock = rewards.getFirst().options().getFirst();
            assertThat(optionStock.availableStock()).isNull();
            assertThat(optionStock.soldOut()).isFalse();
        }

        @Test
        void 재고를_받아오지_못하면_품절로_단정하지_않는다() {
            // given
            when(rewardRepository.findActiveByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(RewardFixture.reward()));
            when(rewardOptionRepository.findActiveByRewardIds(List.of(RewardFixture.REWARD_ID)))
                    .thenReturn(List.of(RewardFixture.option()));
            when(inventoryPort.findAvailableStocks(List.of(RewardFixture.OPTION_ID))).thenReturn(Map.of());

            // when
            var rewards = rewardQueryService.listWithStock(ProjectFixture.PUBLIC_ID);

            // then — 연동 실패를 품절로 표시하면 판매 가능한 옵션이 사라진 것처럼 보인다
            var optionStock = rewards.getFirst().options().getFirst();
            assertThat(optionStock.availableStock()).isNull();
            assertThat(optionStock.soldOut()).isFalse();
        }

        @Test
        void 옵션은_소속_리워드에만_붙는다() {
            // given
            when(rewardRepository.findActiveByProjectId(ProjectFixture.PROJECT_ID)).thenReturn(List.of(
                    RewardFixture.reward(),
                    RewardFixture.base().id(502L).build()));
            when(rewardOptionRepository.findActiveByRewardIds(List.of(RewardFixture.REWARD_ID, 502L)))
                    .thenReturn(List.of(
                            RewardFixture.option(),
                            RewardFixture.optionBase().id(9002L).rewardId(502L).sku("SKU-502").build()));
            when(inventoryPort.findAvailableStocks(List.of(RewardFixture.OPTION_ID, 9002L)))
                    .thenReturn(Map.of(RewardFixture.OPTION_ID, 3, 9002L, 5));

            // when
            var rewards = rewardQueryService.listWithStock(ProjectFixture.PUBLIC_ID);

            // then
            assertThat(rewards.getFirst().options()).hasSize(1);
            assertThat(rewards.getFirst().options().getFirst().option().getId()).isEqualTo(RewardFixture.OPTION_ID);
            assertThat(rewards.getLast().options().getFirst().option().getId()).isEqualTo(9002L);
        }
    }

    @Nested
    class 고시_조회 {

        @Test
        void 살아있는_리워드만_돌려준다() {
            // given
            when(rewardRepository.findActiveByProjectId(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(RewardFixture.reward()));

            // when
            var disclosures = rewardQueryService.listDisclosures(ProjectFixture.PUBLIC_ID);

            // then
            assertThat(disclosures).hasSize(1);
            assertThat(disclosures.getFirst().getDisclosure()).containsEntry("모델명", "H-100");
        }
    }
}
