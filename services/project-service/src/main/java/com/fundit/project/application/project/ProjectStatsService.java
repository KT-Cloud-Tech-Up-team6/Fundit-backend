package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.fundingstatus.RewardStat;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaEntity;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.opennotify.ProjectOpenNotifyRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.wishstats.ProjectWishStatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 펀딩 현황 조회, 찜·알림신청 건수 조회(판매자용) — PROJECT-015, PROJECT-016. */
@Service
@RequiredArgsConstructor
public class ProjectStatsService {

    private final ProjectRepository projectRepository;
    private final FundingStatusSnapshotJpaRepository fundingStatusSnapshotJpaRepository;
    private final ProjectWishStatJpaRepository wishStatJpaRepository;
    private final ProjectOpenNotifyRequestJpaRepository openNotifyRequestJpaRepository;

    @Transactional(readOnly = true)
    public FundingStatusView getFundingStatus(UUID sellerId, UUID projectPublicId) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        FundingStatusSnapshotJpaEntity snapshot = fundingStatusSnapshotJpaRepository.findById(project.getId()).orElse(null);

        long currentAmount = snapshot != null ? snapshot.getCurrentAmount() : 0L;
        int achievementRate = snapshot != null ? snapshot.getAchievementRate() : 0;
        int participantCount = snapshot != null ? snapshot.getParticipantCount() : 0;
        List<RewardStat> rewardStats = snapshot != null && snapshot.getRewardStats() != null ? snapshot.getRewardStats() : List.of();
        Instant lastSyncedAt = snapshot != null ? snapshot.getLastSyncedAt() : null;

        long openNotifyCount = openNotifyRequestJpaRepository.countByProjectId(project.getId());
        int wishCount = wishStatJpaRepository.findById(project.getId()).map(w -> w.getWishCount()).orElse(0);
        Long remainingDays = remainingDays(project);

        return new FundingStatusView(currentAmount, achievementRate, participantCount,
                openNotifyCount, wishCount, rewardStats, remainingDays, lastSyncedAt);
    }

    @Transactional(readOnly = true)
    public WishStatsView getWishStats(UUID sellerId, UUID projectPublicId) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        int wishCount = wishStatJpaRepository.findById(project.getId()).map(w -> w.getWishCount()).orElse(0);
        long openNotifyCount = openNotifyRequestJpaRepository.countByProjectId(project.getId());
        return new WishStatsView(wishCount, openNotifyCount);
    }

    private Long remainingDays(Project project) {
        if (project.getFundingDeadline() == null) return null;
        Duration remaining = Duration.between(Instant.now(), project.getFundingDeadline());
        return remaining.isNegative() ? 0L : remaining.toDays() + 1;
    }

    private Project loadOwnedProject(UUID sellerId, UUID projectPublicId) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return project;
    }

    public record FundingStatusView(
            long currentAmount, int achievementRate, int participantCount, long openNotifyCount, int wishCount,
            List<RewardStat> rewardStats, Long remainingDays, Instant lastSyncedAt) {
    }

    public record WishStatsView(int wishCount, long openNotifyCount) {
    }
}
