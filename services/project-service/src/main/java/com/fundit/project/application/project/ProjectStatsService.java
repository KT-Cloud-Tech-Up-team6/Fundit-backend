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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 펀딩 현황 조회, 찜·알림신청 건수 조회(판매자용) — PROJECT-015, PROJECT-016. */
@Service
@RequiredArgsConstructor
public class ProjectStatsService {

    private static final Logger log = LoggerFactory.getLogger(ProjectStatsService.class);

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

    /**
     * 목록 화면용 배치 조회 — 카드 수만큼 단건 조회를 반복하지 않도록 프로젝트 목록 페이지의
     * projectId(내부 PK) 전체를 한 번에 조회한다. 없는 프로젝트는 결과 맵에서 생략된다
     * (호출부가 기본값 0으로 처리).
     */
    @Transactional(readOnly = true)
    public Map<Long, FundingStatusSnapshotView> getFundingStatusBatch(List<Long> projectIds) {
        return fundingStatusSnapshotJpaRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(FundingStatusSnapshotJpaEntity::getProjectId,
                        s -> new FundingStatusSnapshotView(s.getCurrentAmount(), s.getAchievementRate(), s.getParticipantCount())));
    }

    /**
     * order-service {@code project.funding-reward-stats-updated.v1} — reward_stats 전체 교체 +
     * 그 금액 합으로 모금액·달성률 갱신.
     *
     * <p>order-service는 이 서비스의 내부 PK를 모르므로 publicId(UUID)로 보낸다 — 여기서 내부 id로
     * 변환한다. 알 수 없는 publicId(삭제/오탐)면 예외로 파티션을 막지 않고 이 메시지만 건너뛴다
     * (event-convention.md 7번, at-least-once라 재전송돼도 같은 스냅샷으로 수렴하므로 멱등).
     */
    @Transactional
    public void applyRewardStats(UUID projectPublicId, List<RewardStat> rewardStats) {
        Project project = projectRepository.findByPublicId(projectPublicId).orElse(null);
        if (project == null) {
            log.warn("알 수 없는 projectPublicId로 리워드 통계 이벤트 수신, 건너뜀. projectPublicId={}", projectPublicId);
            return;
        }
        Long projectId = project.getId();
        FundingStatusSnapshotJpaEntity snapshot = fundingStatusSnapshotJpaRepository.findById(projectId)
                .orElseGet(() -> FundingStatusSnapshotJpaEntity.builder()
                        .projectId(projectId)
                        .currentAmount(0L)
                        .achievementRate(0)
                        .participantCount(0)
                        .build());
        long currentAmount = sumRewardAmount(rewardStats);
        snapshot.replaceRewardStats(rewardStats);
        snapshot.applyFundingProgress(currentAmount, achievementRate(project.getGoalAmount(), currentAmount));
        // ponytail: participantCount는 그대로 0이다. 이 이벤트에 참여 건수를 셀 필드가 없어
        // order-service가 페이로드에 추가해줘야 채울 수 있다.
        fundingStatusSnapshotJpaRepository.save(snapshot);
    }

    /**
     * 옵션 단위 행({@code optionValueId != null})은 리워드 단위 행과 같은 금액을 쪼개 담고 있어
     * 같이 더하면 중복 계상된다. order-service의 목표 달성 판정({@code FundingGoalJudgmentService})도
     * 리워드 단위 합을 쓰므로 판정 결과와 이 값이 어긋나지 않는다.
     */
    private long sumRewardAmount(List<RewardStat> rewardStats) {
        return rewardStats.stream()
                .filter(s -> s.optionValueId() == null)
                .mapToLong(s -> s.purchasedAmount() == null ? 0L : s.purchasedAmount())
                .sum();
    }

    /** 목표금액이 없는 단계(DRAFT)이거나 0이면 계산할 근거가 없어 0으로 둔다. */
    private int achievementRate(Long goalAmount, long currentAmount) {
        if (goalAmount == null || goalAmount <= 0) {
            return 0;
        }
        return (int) (currentAmount * 100 / goalAmount);
    }

    @Transactional
    public void applyProjectWished(Long projectId, UUID memberId) {
        if (wishStatJpaRepository.insertMemberIfAbsent(projectId, memberId) > 0) {
            wishStatJpaRepository.incrementOrCreate(projectId);
        }
    }

    @Transactional
    public void applyProjectUnwished(Long projectId, UUID memberId) {
        if (wishStatJpaRepository.deleteMember(projectId, memberId) > 0) {
            wishStatJpaRepository.decrementIfPresent(projectId);
        }
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

    public record FundingStatusSnapshotView(long currentAmount, int achievementRate, int participantCount) {
    }
}
