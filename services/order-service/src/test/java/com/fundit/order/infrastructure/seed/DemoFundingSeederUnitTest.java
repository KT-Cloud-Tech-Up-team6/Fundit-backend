package com.fundit.order.infrastructure.seed;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.catalog.RewardCatalogClient;
import com.fundit.order.application.catalog.RewardCatalogClient.RewardSnapshot;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DemoFundingSeederUnitTest {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    private static final UUID BUYER_ID = UUID.fromString("01a0ec1e-e96e-7740-b178-e1e530d21aa6");

    @Mock private FundingJpaRepository fundingRepository;
    @Mock private FundingLineItemJpaRepository lineItemRepository;
    @Mock private RewardCatalogClient rewardCatalogClient;
    @Mock private TransactionTemplate transactionTemplate;

    private DemoFundingSeeder seeder(String buyerMemberId) {
        return new DemoFundingSeeder(fundingRepository, lineItemRepository, rewardCatalogClient, transactionTemplate,
                buyerMemberId, Clock.fixed(BASE, ZoneOffset.UTC));
    }

    @SuppressWarnings("unchecked")
    private void givenTransactionRuns() {
        willAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).given(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void 시연_프로젝트_리워드로_구매자의_성립된_펀딩을_만든다() {
        // given — reward_id는 project 내부 id라 리워드 조회로 받아 온다
        given(fundingRepository.findByPublicId(DemoFundingSeeder.DEMO_FUNDING_ID)).willReturn(Optional.empty());
        given(rewardCatalogClient.getRewards(DemoFundingSeeder.DEMO_PROJECT_ID)).willReturn(List.of(
                new RewardSnapshot(77L, "[얼리버드] 캔버스 수납 바스켓 1개", 30_000L, false, List.of())));
        given(fundingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        givenTransactionRuns();

        // when
        seeder(BUYER_ID.toString()).seed();

        // then
        ArgumentCaptor<FundingJpaEntity> funding = ArgumentCaptor.forClass(FundingJpaEntity.class);
        verify(fundingRepository).save(funding.capture());
        assertThat(funding.getValue().getStatus()).isEqualTo("GOAL_ACHIEVED");
        assertThat(funding.getValue().getMemberId()).isEqualTo(BUYER_ID);
        assertThat(funding.getValue().getProjectPublicId()).isEqualTo(DemoFundingSeeder.DEMO_PROJECT_ID);
        assertThat(funding.getValue().getPaidAt()).isEqualTo(BASE.minus(Duration.ofDays(30)));

        ArgumentCaptor<FundingLineItemJpaEntity> line = ArgumentCaptor.forClass(FundingLineItemJpaEntity.class);
        verify(lineItemRepository).save(line.capture());
        assertThat(line.getValue().getRewardId()).isEqualTo(77L);
        assertThat(line.getValue().getUnitPrice()).isEqualTo(30_000L);
        assertThat(line.getValue().getQuantity()).isEqualTo(1);
    }

    @Test
    void 이미_있으면_만들지_않고_이후_주기엔_아무것도_안_한다() {
        // given
        given(fundingRepository.findByPublicId(DemoFundingSeeder.DEMO_FUNDING_ID))
                .willReturn(Optional.of(FundingJpaEntity.builder().build()));
        DemoFundingSeeder seeder = seeder(BUYER_ID.toString());

        // when
        seeder.seed();
        seeder.seed();

        // then
        verify(fundingRepository, times(1)).findByPublicId(any());
        verifyNoInteractions(rewardCatalogClient, transactionTemplate);
    }

    @Test
    void 구매자_ID가_비어_있으면_건너뛴다() {
        // when
        seeder("").seed();

        // then
        verifyNoInteractions(fundingRepository, rewardCatalogClient);
    }

    @Test
    void 리워드를_아직_못_받으면_저장하지_않고_다음_주기에_다시_시도한다() {
        // given — project 시연 시더가 아직 안 돌았거나 project가 안 떠 있다
        given(fundingRepository.findByPublicId(DemoFundingSeeder.DEMO_FUNDING_ID)).willReturn(Optional.empty());
        given(rewardCatalogClient.getRewards(DemoFundingSeeder.DEMO_PROJECT_ID))
                .willThrow(new DependencyFailureException(new RuntimeException("connect timeout")));
        DemoFundingSeeder seeder = seeder(BUYER_ID.toString());

        // when
        seeder.seed();
        seeder.seed();

        // then
        verify(rewardCatalogClient, times(2)).getRewards(any());
        verify(fundingRepository, never()).save(any());
    }
}
