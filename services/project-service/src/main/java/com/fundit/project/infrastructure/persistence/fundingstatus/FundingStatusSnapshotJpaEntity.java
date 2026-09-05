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
 * 단순 애그리거트 — order-service가 발행하는 펀딩 집계 이벤트를 구독해 채우는 읽기 모델
 * (project-service CLAUDE.md 핵심 설계 결정, PROJECT-015). 메시지 브로커 미구성으로 현재는
 * 구독자가 없어 값이 채워지지 않은 프로젝트는 기본값(0)으로 조회된다(RewardEventPublisher와
 * 동일한 성격의 placeholder 상태 — 이벤트 인프라가 준비되면 실제 구독자를 붙인다).
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
}
