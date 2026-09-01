package com.fundit.project.application.supporterreview;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.infrastructure.persistence.supporterreview.SupporterReviewJpaEntity;
import com.fundit.project.infrastructure.persistence.supporterreview.SupporterReviewJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SupporterReviewService {

    private final SupporterReviewJpaRepository reviewJpaRepository;
    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final FundingPort fundingPort;
    private final MemberPort memberPort;

    /** PROJECT-023. */
    @Transactional(readOnly = true)
    public List<ReviewWithAuthor> list(UUID projectId) {
        UUID viewerId = currentUserProvider.find()
                .map(CurrentUserProvider.CurrentUser::id)
                .orElse(null);
        Project project = accessGuard.findVisible(projectId, viewerId);

        List<SupporterReviewJpaEntity> reviews = reviewJpaRepository
                .findByProjectIdOrderByCreatedAtDesc(project.getId());
        Map<UUID, String> nicknames = memberPort.findNicknames(
                reviews.stream().map(SupporterReviewJpaEntity::getMemberId).distinct().toList());

        return reviews.stream()
                .map(review -> new ReviewWithAuthor(review, nicknames.get(review.getMemberId())))
                .toList();
    }

    /**
     * PROJECT-024. 배송완료 여부는 DB로 강제할 수 없어 order-service에 물어본다.
     * 본인 소유가 아닌 펀딩 건이면 403, 배송이 끝나지 않았으면 422다.
     */
    public SupporterReviewJpaEntity create(UUID projectId, Long fundingId, String content) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findVisible(projectId, currentUser.id());

        FundingPort.ReviewEligibility eligibility =
                fundingPort.checkReviewEligibility(fundingId, currentUser.id());
        if (!eligibility.ownedByMember()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (!eligibility.delivered()) {
            throw new BusinessException(ProjectErrorCode.SUPPORTER_REVIEW_NOT_ELIGIBLE);
        }

        return reviewJpaRepository.save(SupporterReviewJpaEntity.builder()
                .projectId(project.getId())
                .fundingId(fundingId)
                .memberId(currentUser.id())
                .content(content)
                .build());
    }

    public record ReviewWithAuthor(SupporterReviewJpaEntity review, String nickname) {
    }
}
