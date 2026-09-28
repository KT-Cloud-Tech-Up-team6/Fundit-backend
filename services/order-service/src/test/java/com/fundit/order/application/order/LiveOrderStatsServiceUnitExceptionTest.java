package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.live.LiveStatusClient;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveOrderStatsServiceUnitExceptionTest {

    @Mock
    private LiveStatusClient liveStatusClient;
    @Mock
    private FundingJpaRepository fundingJpaRepository;

    @InjectMocks
    private LiveOrderStatsService liveOrderStatsService;

    @Test
    void 방송_소유자가_아니면_FORBIDDEN_예외가_발생한다() {
        // given
        UUID liveId = UUID.randomUUID();
        when(liveStatusClient.findByLiveId(liveId)).thenReturn(
                Optional.of(new LiveStatusClient.LiveStatus(liveId, 42L, "LIVE", UUID.randomUUID())));

        // when & then
        assertThatThrownBy(() -> liveOrderStatsService.getStats(UUID.randomUUID(), liveId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
        verify(fundingJpaRepository, never()).findLiveOrderStats(anyLong());
    }

    @Test
    void 세션이_없으면_NOT_FOUND_예외가_발생한다() {
        // given
        UUID liveId = UUID.randomUUID();
        when(liveStatusClient.findByLiveId(liveId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> liveOrderStatsService.getStats(UUID.randomUUID(), liveId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void live_조회에_실패하면_예외가_그대로_전파된다() {
        // given — 503으로 끝난다. 집계는 부분 결과를 내놓는 것보다 실패를 알리는 쪽이 맞다.
        UUID liveId = UUID.randomUUID();
        when(liveStatusClient.findByLiveId(liveId))
                .thenThrow(new DependencyFailureException(new IllegalStateException("timeout")));

        // when & then
        assertThatThrownBy(() -> liveOrderStatsService.getStats(UUID.randomUUID(), liveId))
                .isInstanceOf(DependencyFailureException.class);
    }
}
