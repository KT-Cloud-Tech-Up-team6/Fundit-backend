package com.fundit.project.infrastructure.persistence.fundingstatus;

import com.fundit.project.domain.fundingstatus.RewardStat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * 단순 애그리거트 — order-service {@code project.funding-reward-stats-updated.v1} 를 구독해 채우는 읽기 모델.
 * reward_stats 를 교체하면서 그 금액 합으로 currentAmount·achievementRate 도 같이 갱신한다.
 * participantCount 만 아직 0이다 — 이벤트에 참여자 수를 셀 수 있는 필드가 없다.
 */
@Getter
@Entity
@Builder
@Table(name = "funding_status_snapshots")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundingStatusSnapshotJpaEntity {

    @Id
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "current_amount", nullable = false)
    private Long currentAmount;

    @Column(name = "achievement_rate", nullable = false)
    private Integer achievementRate;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reward_stats", columnDefinition = "jsonb")
    private List<RewardStat> rewardStats;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    public void replaceRewardStats(List<RewardStat> rewardStats) {
        this.rewardStats = rewardStats;
        this.lastSyncedAt = Instant.now();
    }

    /** 모금액과 달성률은 같은 이벤트에서 함께 계산되므로 한 번에 받는다. */
    public void applyFundingProgress(long currentAmount, int achievementRate) {
        this.currentAmount = currentAmount;
        this.achievementRate = achievementRate;
    }
}
