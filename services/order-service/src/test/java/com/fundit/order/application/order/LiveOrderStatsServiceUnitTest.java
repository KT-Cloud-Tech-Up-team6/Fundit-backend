package com.fundit.order.application.order;

import com.fundit.order.application.live.LiveStatusClient;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.query.LiveOrderStatsProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveOrderStatsServiceUnitTest {

    @Mock
    private LiveStatusClient liveStatusClient;
    @Mock
    private FundingJpaRepository fundingJpaRepository;
    @Mock
    private LiveOrderStatsProjection stats;

    @InjectMocks
    private LiveOrderStatsService liveOrderStatsService;

    @Test
    void 방송_소유자가_조회하면_세션ID로_집계한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        when(liveStatusClient.findByLiveId(liveId))
                .thenReturn(Optional.of(new LiveStatusClient.LiveStatus(liveId, 42L, "LIVE", sellerId)));
        when(fundingJpaRepository.findLiveOrderStats(42L)).thenReturn(stats);

        // when
        LiveOrderStatsProjection result = liveOrderStatsService.getStats(sellerId, liveId);

        // then
        assertThat(result).isSameAs(stats);
    }

    @Test
    void 방송이_끝났어도_집계는_그대로_조회된다() {
        // given — 종료 직후에도 판매자가 결과를 본다. 게이트가 아니라 조회라 상태를 따지지 않는다.
        UUID sellerId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        when(liveStatusClient.findByLiveId(liveId))
                .thenReturn(Optional.of(new LiveStatusClient.LiveStatus(liveId, 7L, "ENDED", sellerId)));
        when(fundingJpaRepository.findLiveOrderStats(7L)).thenReturn(stats);

        // when & then
        assertThat(liveOrderStatsService.getStats(sellerId, liveId)).isSameAs(stats);
    }
}
