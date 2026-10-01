package com.fundit.search.application.projectdocument;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * SEARCH-013. 카드의 모금액·달성률·참여자 수를 order-service 집계 스냅샷으로 갱신한다.
 * 계산 규칙은 project-service {@code ProjectStatsService.applyRewardStats}와 같다 —
 * 상세와 카드가 같은 이벤트를 원천으로 쓰므로 두 화면 값이 어긋나지 않는다.
 *
 * <p>색인에 없는 publicId는 예외로 파티션을 막지 않고 이 메시지만 건너뛴다(project-service와 같은 규칙,
 * event-convention.md 7번). SEARCH-012와 달리 {@link SearchIndexNotReadyException}으로 재시도하지 않는 이유:
 * 이 이벤트는 1일 배치라 다음 주기에 같은 스냅샷이 다시 와서 수렴한다 — 짧은 백오프를 소진해 DLT로 보내도
 * 얻는 게 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectDocumentFundingStatsSyncService implements FundingRewardStatsEventListener {

    private final ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @Override
    @Transactional
    public void onRewardStatsUpdated(RewardStatsUpdatedEvent event) {
        long currentAmount = sumRewardAmount(event.rewardStats());
        int updated = projectDocumentJpaRepository.updateFundingStats(
                event.projectId(), currentAmount, event.participantCount());
        if (updated == 0) {
            log.warn("색인에 없는 projectPublicId로 펀딩 통계 이벤트 수신, 건너뜀. projectPublicId={}", event.projectId());
        }
    }

    /** 달성률은 색인 행의 goal_amount로 SQL에서 계산한다 — 여기서 읽어올 값이 아니다. */
    private long sumRewardAmount(List<RewardStat> rewardStats) {
        if (rewardStats == null) {
            return 0L;
        }
        return rewardStats.stream()
                .filter(s -> s.optionValueId() == null)
                .mapToLong(s -> s.purchasedAmount() == null ? 0L : s.purchasedAmount())
                .sum();
    }
}
