package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaEntity;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 프로젝트 미리보기(판매자)·공개 상세·환불정책 조회 — PROJECT-013, PROJECT-020, PROJECT-026. */
@Service
@RequiredArgsConstructor
public class ProjectQueryService {

    private static final String COMMON_SIMPLE_REFUND_DEADLINE = "펀딩 마감 전까지";
    private static final boolean COMMON_GOAL_FAILED_AUTO_REFUND = true;

    private final ProjectRepository projectRepository;
    private final FundingStatusSnapshotJpaRepository fundingStatusSnapshotJpaRepository;
    private final LiveVerificationJpaRepository liveVerificationJpaRepository;
    private final RewardJpaRepository rewardJpaRepository;
    private final SellerProfileClient sellerProfileClient;

    /** 미공개(DRAFT/PENDING_REVIEW) 상태여도 본인 소유면 조회 가능(PROJECT-013). */
    @Transactional(readOnly = true)
    public ProjectDetailView getPreview(UUID sellerId, UUID projectPublicId) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return toDetailView(project);
    }

    /**
     * 공개 상태(ONGOING/SUCCEEDED/FAILED)가 아니면 존재 여부를 노출하지 않기 위해 404로 응답한다
     * (project-service CLAUDE.md "미공개 프로젝트 존재 여부 비노출" 원칙, PROJECT-020).
     */
    @Transactional(readOnly = true)
    public ProjectDetailView getPublicDetail(UUID projectPublicId) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .filter(Project::isPublic)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return toDetailView(project);
    }

    @Transactional(readOnly = true)
    public RefundPolicyView getRefundPolicy(UUID projectPublicId) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .filter(Project::isPublic)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        List<RewardPolicyView> rewardPolicies = rewardJpaRepository
                .findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(project.getId()).stream()
                .map(r -> new RewardPolicyView(r.getId(), r.getSimpleRefundDisabled()))
                .toList();

        return new RefundPolicyView(
                new CommonPolicyView(COMMON_SIMPLE_REFUND_DEADLINE, COMMON_GOAL_FAILED_AUTO_REFUND),
                rewardPolicies);
    }

    private ProjectDetailView toDetailView(Project project) {
        FundingStatusSnapshotJpaEntity snapshot = fundingStatusSnapshotJpaRepository.findById(project.getId()).orElse(null);
        long currentAmount = snapshot != null ? snapshot.getCurrentAmount() : 0L;
        int achievementRate = snapshot != null ? snapshot.getAchievementRate() : 0;
        int participantCount = snapshot != null ? snapshot.getParticipantCount() : 0;
        Long remainingDays = remainingDays(project);

        boolean hasLiveVerification = liveVerificationJpaRepository.existsByProjectIdAndDeletedAtIsNull(project.getId());
        String displayName = sellerProfileClient.getDisplayName(project.getSellerId()).orElse(null);

        return new ProjectDetailView(project.getPublicId(), project.getTitle(), project.getStatus().name(),
                project.getGoalAmount(),
                new FundingStatusView(currentAmount, achievementRate, participantCount, remainingDays),
                hasLiveVerification, new SellerView(project.getSellerId(), displayName));
    }

    private Long remainingDays(Project project) {
        if (project.getFundingDeadline() == null) return null;
        Duration remaining = Duration.between(Instant.now(), project.getFundingDeadline());
        return remaining.isNegative() ? 0L : remaining.toDays() + 1;
    }

    public record FundingStatusView(long currentAmount, int achievementRate, int participantCount, Long remainingDays) {
    }

    public record SellerView(UUID sellerId, String displayName) {
    }

    public record ProjectDetailView(
            UUID projectId, String title, String status, Long goalAmount,
            FundingStatusView fundingStatus, boolean hasLiveVerification, SellerView seller) {
    }

    public record CommonPolicyView(String simpleRefundDeadline, boolean goalFailedAutoRefund) {
    }

    public record RewardPolicyView(Long rewardId, boolean simpleRefundDisabled) {
    }

    public record RefundPolicyView(CommonPolicyView commonPolicy, List<RewardPolicyView> rewardPolicies) {
    }
}
