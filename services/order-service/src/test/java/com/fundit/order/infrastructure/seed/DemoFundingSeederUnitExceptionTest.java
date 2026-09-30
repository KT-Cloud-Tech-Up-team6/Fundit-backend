package com.fundit.order.infrastructure.seed;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.catalog.RewardCatalogClient;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoFundingSeederUnitExceptionTest {

    @Mock private FundingJpaRepository fundingRepository;
    @Mock private FundingLineItemJpaRepository lineItemRepository;
    @Mock private RewardCatalogClient rewardCatalogClient;
    @Mock private TransactionTemplate transactionTemplate;

    @Test
    void 리워드를_아직_못_받으면_저장하지_않고_다음_주기에_다시_시도한다() {
        // given — project 시연 시더가 아직 안 돌았거나 project가 안 떠 있다
        DemoFundingSeeder seeder = new DemoFundingSeeder(fundingRepository, lineItemRepository, rewardCatalogClient,
                transactionTemplate, "01a0ec1e-e96e-7740-b178-e1e530d21aa6",
                Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
        given(fundingRepository.findByPublicId(DemoFundingSeeder.DEMO_FUNDING_ID)).willReturn(Optional.empty());
        given(rewardCatalogClient.getRewards(DemoFundingSeeder.DEMO_PROJECT_ID))
                .willThrow(new DependencyFailureException(new RuntimeException("connect timeout")));

        // when
        seeder.seed();
        seeder.seed();

        // then
        verify(rewardCatalogClient, times(2)).getRewards(any());
        verify(fundingRepository, never()).save(any());
    }
}
