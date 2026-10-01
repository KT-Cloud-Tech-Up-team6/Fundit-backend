package com.fundit.search.application.projectdocument;

import com.fundit.search.application.projectdocument.FundingRewardStatsEventListener.RewardStat;
import com.fundit.search.application.projectdocument.FundingRewardStatsEventListener.RewardStatsUpdatedEvent;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectDocumentFundingStatsSyncServiceUnitTest {

    private static final UUID PUBLIC_ID = UUID.fromString("80f88d7e-089a-5226-b306-9e7168853e60");

    @Mock
    private ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @InjectMocks
    private ProjectDocumentFundingStatsSyncService service;

    @Test
    void 모금액은_옵션값_행을_빼고_리워드_합계_행만_더한다() {
        // given — 옵션 행(optionValueId != null)은 리워드 행과 같은 금액을 쪼개 담고 있어 더하면 중복 계상된다
        var event = new RewardStatsUpdatedEvent(PUBLIC_ID, List.of(
                new RewardStat(1L, null, 3, 30_000L),
                new RewardStat(1L, 11L, 2, 20_000L),
                new RewardStat(1L, 12L, 1, 10_000L),
                new RewardStat(2L, null, 1, 70_000L)), 4);

        // when
        service.onRewardStatsUpdated(event);

        // then
        verify(projectDocumentJpaRepository).updateFundingStats(PUBLIC_ID, 100_000L, 4);
    }

    @Test
    void 금액이_null인_행은_0으로_더한다() {
        // given
        var event = new RewardStatsUpdatedEvent(PUBLIC_ID, List.of(
                new RewardStat(1L, null, 1, null),
                new RewardStat(2L, null, 1, 5_000L)), 1);

        // when
        service.onRewardStatsUpdated(event);

        // then
        verify(projectDocumentJpaRepository).updateFundingStats(PUBLIC_ID, 5_000L, 1);
    }

    @Test
    void 참여자수가_null이면_그대로_넘겨_기존_값을_유지한다() {
        // given — 필드가 없던 구버전 order 메시지
        var event = new RewardStatsUpdatedEvent(PUBLIC_ID, List.of(new RewardStat(1L, null, 1, 9_000L)), null);

        // when
        service.onRewardStatsUpdated(event);

        // then
        verify(projectDocumentJpaRepository).updateFundingStats(PUBLIC_ID, 9_000L, null);
    }

    @Test
    void 리워드_목록이_null이면_모금액은_0이다() {
        // given
        var event = new RewardStatsUpdatedEvent(PUBLIC_ID, null, 0);

        // when
        service.onRewardStatsUpdated(event);

        // then
        verify(projectDocumentJpaRepository).updateFundingStats(PUBLIC_ID, 0L, 0);
    }

    @Test
    void 색인에_없는_프로젝트면_예외없이_건너뛴다() {
        // given — 1일 배치라 다음 주기에 같은 스냅샷이 다시 온다. 예외로 파티션을 막지 않는다.
        given(projectDocumentJpaRepository.updateFundingStats(PUBLIC_ID, 1_000L, 1)).willReturn(0);
        var event = new RewardStatsUpdatedEvent(PUBLIC_ID, List.of(new RewardStat(1L, null, 1, 1_000L)), 1);

        // when
        service.onRewardStatsUpdated(event);

        // then
        verify(projectDocumentJpaRepository).updateFundingStats(PUBLIC_ID, 1_000L, 1);
    }
}
