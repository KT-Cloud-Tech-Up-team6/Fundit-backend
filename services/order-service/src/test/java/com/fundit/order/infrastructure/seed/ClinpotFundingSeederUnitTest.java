package com.fundit.order.infrastructure.seed;

import com.fundit.order.application.catalog.RewardCatalogClient;
import com.fundit.order.application.catalog.RewardCatalogClient.OptionGroupSnapshot;
import com.fundit.order.application.catalog.RewardCatalogClient.OptionValueSnapshot;
import com.fundit.order.application.catalog.RewardCatalogClient.RewardSnapshot;
import com.fundit.order.application.funding.FundingRewardStatsBatchService;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.inventory.InventoryRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemOptionJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemOptionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ClinpotFundingSeederUnitTest {

    private static final Instant BASE = Instant.parse("2026-10-02T00:00:00Z");
    /** 얼리버드(한정) 269,000원 — 할인가는 카탈로그 클라이언트가 이미 접어서 준다. */
    private static final RewardSnapshot EARLY_BIRD = new RewardSnapshot(26L, "얼리버드 클린팟 1대", 269_000L, true,
            List.of(new OptionGroupSnapshot(12L, "색상",
                    List.of(new OptionValueSnapshot(26L, "퓨어 화이트"), new OptionValueSnapshot(27L, "웜 그레이")))));
    private static final RewardSnapshot BASIC = new RewardSnapshot(27L, "클린팟 기본 패키지", 319_000L, false,
            List.of(new OptionGroupSnapshot(13L, "색상",
                    List.of(new OptionValueSnapshot(28L, "퓨어 화이트"), new OptionValueSnapshot(29L, "웜 그레이")))));

    @Mock private FundingJpaRepository fundingRepository;
    @Mock private FundingLineItemJpaRepository lineItemRepository;
    @Mock private FundingLineItemOptionJpaRepository optionRepository;
    @Mock private RewardCatalogClient rewardCatalogClient;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private FundingRewardStatsBatchService rewardStatsBatchService;
    @Mock private TransactionTemplate transactionTemplate;

    private ClinpotFundingSeeder seeder() {
        return new ClinpotFundingSeeder(fundingRepository, lineItemRepository, optionRepository, rewardCatalogClient,
                inventoryRepository, rewardStatsBatchService, transactionTemplate, Clock.fixed(BASE, ZoneOffset.UTC));
    }

    @SuppressWarnings("unchecked")
    private void givenTransactionRuns() {
        willAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).given(transactionTemplate).executeWithoutResult(any());
    }

    private static final FundingJpaEntity EXISTING = FundingJpaEntity.builder().build();

    private void givenCloneAlreadySeeded() {
        given(fundingRepository.findByPublicId(ClinpotFundingSeeder.fundingId(ClinpotFundingSeeder.CLONE, 0)))
                .willReturn(Optional.of(EXISTING));
    }

    private void givenSeedable() {
        givenCloneAlreadySeeded();
        given(fundingRepository.findByPublicId(ClinpotFundingSeeder.fundingId(ClinpotFundingSeeder.ORIGINAL, 0))).willReturn(Optional.empty());
        given(rewardCatalogClient.getRewards(ClinpotFundingSeeder.PROJECT_ID)).willReturn(List.of(EARLY_BIRD, BASIC));
        given(fundingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(lineItemRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(inventoryRepository.decreaseStock(any(), org.mockito.ArgumentMatchers.anyInt())).willReturn(true);
        givenTransactionRuns();
    }

    @Test
    void 서로_다른_구매자로_펀딩_30건을_만든다() {
        // given
        givenSeedable();

        // when
        seeder().seed();

        // then
        ArgumentCaptor<FundingJpaEntity> captor = ArgumentCaptor.forClass(FundingJpaEntity.class);
        verify(fundingRepository, times(30)).save(captor.capture());
        List<FundingJpaEntity> saved = captor.getAllValues();
        // 후원자 수가 count(DISTINCT member_id)라 구매자가 겹치면 30명으로 안 잡힌다
        assertThat(saved).extracting(FundingJpaEntity::getMemberId).doesNotHaveDuplicates().hasSize(30);
        assertThat(saved).extracting(FundingJpaEntity::getPublicId).doesNotHaveDuplicates();
        // 집계 쿼리가 보는 상태 — 다른 상태면 0원으로 남는다
        assertThat(saved).allMatch(f -> FundingStatus.FUNDING_IN_PROGRESS.name().equals(f.getStatus()));
    }

    @Test
    void 얼리버드_10건_기본_20건으로_9_070_000원이_된다() {
        // given
        givenSeedable();

        // when
        seeder().seed();

        // then
        ArgumentCaptor<FundingLineItemJpaEntity> captor = ArgumentCaptor.forClass(FundingLineItemJpaEntity.class);
        verify(lineItemRepository, times(30)).save(captor.capture());
        List<FundingLineItemJpaEntity> items = captor.getAllValues();
        assertThat(items).filteredOn(i -> i.getRewardId() == 26L).hasSize(10);
        assertThat(items).filteredOn(i -> i.getRewardId() == 27L).hasSize(20);
        // 정가(300,000·350,000)가 아니라 얼리버드 할인가가 들어가야 한다
        assertThat(items.stream().mapToLong(i -> i.getUnitPrice() * i.getQuantity()).sum()).isEqualTo(9_070_000L);
    }

    @Test
    void 한정_리워드만_재고를_차감한다() {
        // given
        givenSeedable();

        // when
        seeder().seed();

        // then
        verify(inventoryRepository).decreaseStock(26L, 10);
        verify(inventoryRepository, org.mockito.Mockito.never())
                .decreaseStock(eq(27L), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void 옵션_행을_함께_저장하고_저장_뒤에_집계를_부른다() {
        // given
        givenSeedable();

        // when
        seeder().seed();

        // then
        ArgumentCaptor<FundingLineItemOptionJpaEntity> captor =
                ArgumentCaptor.forClass(FundingLineItemOptionJpaEntity.class);
        verify(optionRepository, times(30)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(FundingLineItemOptionJpaEntity::getOptionValueId)
                .containsOnly(26L, 27L, 28L, 29L);
        // 집계가 네이티브 쿼리라 저장 뒤여야 한다(#230)
        var order = org.mockito.Mockito.inOrder(lineItemRepository, rewardStatsBatchService);
        order.verify(lineItemRepository, times(30)).save(any());
        order.verify(rewardStatsBatchService).recomputeOne(ClinpotFundingSeeder.PROJECT_ID);
    }

    @Test
    void 이미_넣었으면_아무것도_하지_않는다() {
        // given
        given(fundingRepository.findByPublicId(ClinpotFundingSeeder.fundingId(ClinpotFundingSeeder.ORIGINAL, 0)))
                .willReturn(Optional.of(EXISTING));
        givenCloneAlreadySeeded();

        // when
        seeder().seed();

        // then
        verifyNoInteractions(rewardCatalogClient, lineItemRepository, optionRepository, inventoryRepository,
                rewardStatsBatchService);
    }

    @Test
    void 리워드를_아직_못_받으면_다음_주기에_다시_시도한다() {
        // given
        givenCloneAlreadySeeded();
        given(fundingRepository.findByPublicId(ClinpotFundingSeeder.fundingId(ClinpotFundingSeeder.ORIGINAL, 0))).willReturn(Optional.empty());
        given(rewardCatalogClient.getRewards(ClinpotFundingSeeder.PROJECT_ID))
                .willThrow(new IllegalStateException("project-service down"));

        // when
        ClinpotFundingSeeder seeder = seeder();
        seeder.seed();
        seeder.seed();

        // then
        verify(rewardCatalogClient, times(2)).getRewards(ClinpotFundingSeeder.PROJECT_ID);
        verifyNoInteractions(lineItemRepository, optionRepository, inventoryRepository, rewardStatsBatchService);
    }

    private void givenCloneSeedable() {
        given(fundingRepository.findByPublicId(ClinpotFundingSeeder.fundingId(ClinpotFundingSeeder.ORIGINAL, 0)))
                .willReturn(Optional.of(EXISTING));
        given(fundingRepository.findByPublicId(ClinpotFundingSeeder.fundingId(ClinpotFundingSeeder.CLONE, 0)))
                .willReturn(Optional.empty());
        given(rewardCatalogClient.getRewards(ClinpotFundingSeeder.CLONE_PROJECT_ID)).willReturn(List.of(EARLY_BIRD, BASIC));
        given(fundingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(lineItemRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        givenTransactionRuns();
    }

    @Test
    void 원본이_이미_있어도_클론은_성립_펀딩_31건으로_넣는다() {
        // given
        givenCloneSeedable();

        // when
        seeder().seed();

        // then
        ArgumentCaptor<FundingJpaEntity> captor = ArgumentCaptor.forClass(FundingJpaEntity.class);
        verify(fundingRepository, times(31)).save(captor.capture());
        List<FundingJpaEntity> saved = captor.getAllValues();
        assertThat(saved).allMatch(f -> ClinpotFundingSeeder.CLONE_PROJECT_ID.equals(f.getProjectPublicId()));
        // 펀딩 내역의 "제작·배송 현황"은 GOAL_ACHIEVED에서만 열린다
        assertThat(saved).allMatch(f -> FundingStatus.GOAL_ACHIEVED.name().equals(f.getStatus()) && f.getDecidedAt() != null);
        assertThat(saved).extracting(FundingJpaEntity::getMemberId).doesNotHaveDuplicates();
        // 판매자·소비자 시연을 한 계정으로 한다(PM) — 그 계정 펀딩이 있어야 소비자 화면에 클론이 보인다
        assertThat(saved).extracting(FundingJpaEntity::getMemberId).contains(ClinpotFundingSeeder.DEMO_MEMBER_ID);
        // 원본 펀딩과 publicId가 겹치면 "이미 있음"으로 둘 중 하나가 영영 안 들어간다
        assertThat(saved).extracting(FundingJpaEntity::getPublicId)
                .doesNotContain(ClinpotFundingSeeder.fundingId(ClinpotFundingSeeder.ORIGINAL, 0));
    }

    @Test
    void 클론은_재고를_차감하지_않고_저장_뒤에_클론을_집계한다() {
        // given
        givenCloneSeedable();

        // when
        seeder().seed();

        // then
        verifyNoInteractions(inventoryRepository);
        var order = org.mockito.Mockito.inOrder(lineItemRepository, rewardStatsBatchService);
        order.verify(lineItemRepository, times(31)).save(any());
        order.verify(rewardStatsBatchService).recomputeOne(ClinpotFundingSeeder.CLONE_PROJECT_ID);
    }

    @Test
    void 클론_리워드가_아직_없으면_다음_주기에_다시_시도한다() {
        // given
        given(fundingRepository.findByPublicId(ClinpotFundingSeeder.fundingId(ClinpotFundingSeeder.ORIGINAL, 0)))
                .willReturn(Optional.of(EXISTING));
        given(fundingRepository.findByPublicId(ClinpotFundingSeeder.fundingId(ClinpotFundingSeeder.CLONE, 0)))
                .willReturn(Optional.empty());
        given(rewardCatalogClient.getRewards(ClinpotFundingSeeder.CLONE_PROJECT_ID)).willReturn(List.of());

        // when
        ClinpotFundingSeeder seeder = seeder();
        seeder.seed();
        seeder.seed();

        // then
        verify(rewardCatalogClient, times(2)).getRewards(ClinpotFundingSeeder.CLONE_PROJECT_ID);
        verifyNoInteractions(transactionTemplate);
    }
}
