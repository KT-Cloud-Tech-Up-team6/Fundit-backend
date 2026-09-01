package com.fundit.project.domain.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.ProjectErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 프로젝트 애그리거트 루트. 상태 전이 규칙(DRAFT → PENDING_REVIEW → ONGOING | DRAFT)을 소유한다.
 * status는 submitForReview/approveReview/rejectReview 이외의 경로로 바뀌지 않는다.
 */
@Getter
@Builder
public class Project {

    private static final long MIN_GOAL_AMOUNT = 500_000L;
    private static final int MAX_TITLE_LENGTH = 40;

    private final Long id;
    private final UUID publicId;
    private final UUID sellerId;

    private String categoryMajor;
    private String categoryMinor;
    private BusinessType businessType;
    private boolean privacyAgreed;
    private String title;
    private String thumbnailImageUrl;
    private Map<String, Object> introContent;
    private Long goalAmount;
    private Instant fundingStartAt;
    private Instant fundingDeadline;
    private ProjectStatus status;

    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public static Project createDraft(UUID sellerId) {
        return Project.builder()
                .publicId(UUID.randomUUID())
                .sellerId(sellerId)
                .status(ProjectStatus.DRAFT)
                .privacyAgreed(false)
                .build();
    }

    public boolean isOwnedBy(UUID memberId) {
        return memberId != null && sellerId.equals(memberId);
    }

    public boolean isVisibleTo(UUID viewerId) {
        return status.isPublic() || isOwnedBy(viewerId);
    }

    /**
     * 검수 중에는 어떤 수정 API도 통과시키지 않는다. 검수 재요청만 별도로 409를 쓰고,
     * 나머지 수정 시도는 전부 423 RESOURCE_LOCKED다(ProjectFunctionalSpec 에러 코드 매핑표).
     */
    public void ensureModifiable() {
        if (status == ProjectStatus.PENDING_REVIEW) {
            throw new BusinessException(CommonErrorCode.RESOURCE_LOCKED);
        }
    }

    /**
     * 상세페이지 저장 응답의 savedAt이 이 값을 그대로 쓴다. DB 트리거·@PreUpdate는 flush 시점에
     * 동작해서 저장 직후 응답에는 반영되지 않으므로, 변경 시점을 도메인에서 직접 남긴다.
     */
    private void touch() {
        this.updatedAt = Instant.now();
    }

    public void updateBasicInfo(BusinessType businessType,
                                String categoryMajor,
                                String categoryMinor,
                                Long goalAmount,
                                boolean privacyAgreed) {
        ensureModifiable();
        if (!privacyAgreed) {
            throw new BusinessException(ProjectErrorCode.PRIVACY_NOT_AGREED);
        }
        if (goalAmount == null || goalAmount < MIN_GOAL_AMOUNT) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "목표 금액은 %d원 이상이어야 합니다.".formatted(MIN_GOAL_AMOUNT));
        }
        this.businessType = businessType;
        this.categoryMajor = categoryMajor;
        this.categoryMinor = categoryMinor;
        this.goalAmount = goalAmount;
        this.privacyAgreed = true;
        touch();
    }

    /** 임시저장. 필수값이 비어 있어도 저장을 허용해 작성 중 정보 유실을 막는다. */
    public void updateDetail(String title, String thumbnailImageUrl, Map<String, Object> introContent) {
        ensureModifiable();
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "제목은 %d자 이내여야 합니다.".formatted(MAX_TITLE_LENGTH));
        }
        if (title != null) {
            this.title = title;
        }
        if (thumbnailImageUrl != null) {
            this.thumbnailImageUrl = thumbnailImageUrl;
        }
        if (introContent != null) {
            this.introContent = introContent;
        }
        touch();
    }

    /**
     * 검수 요청(detail.isDraft=false). 리워드 보유 여부는 다른 애그리거트라 애플리케이션이 넘겨준다.
     */
    public void submitForReview(boolean hasReward) {
        if (status == ProjectStatus.PENDING_REVIEW) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 검수 요청된 프로젝트입니다.");
        }
        if (status != ProjectStatus.DRAFT) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "작성 중인 프로젝트만 검수를 요청할 수 있습니다.");
        }

        List<String> missing = new ArrayList<>();
        if (isBlank(title)) {
            missing.add("title");
        }
        if (isBlank(thumbnailImageUrl)) {
            missing.add("thumbnailImageUrl");
        }
        if (introContent == null || introContent.isEmpty()) {
            missing.add("introContent");
        }
        if (!hasReward) {
            missing.add("rewards");
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "검수 요청에 필요한 항목이 누락되었습니다: " + String.join(", ", missing));
        }

        this.status = ProjectStatus.PENDING_REVIEW;
        touch();
    }

    public void approveReview(Instant fundingStartAt, Instant fundingDeadline) {
        requirePendingReview();
        if (fundingStartAt == null || fundingDeadline == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "승인 시에는 펀딩 시작일시와 마감일시가 모두 필요합니다.");
        }
        if (!fundingDeadline.isAfter(fundingStartAt)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "펀딩 마감일시는 시작일시보다 뒤여야 합니다.");
        }
        this.fundingStartAt = fundingStartAt;
        this.fundingDeadline = fundingDeadline;
        this.status = ProjectStatus.ONGOING;
        touch();
    }

    public void rejectReview() {
        requirePendingReview();
        this.status = ProjectStatus.DRAFT;
        touch();
    }

    private void requirePendingReview() {
        if (status != ProjectStatus.PENDING_REVIEW) {
            throw new BusinessException(ProjectErrorCode.PROJECT_REVIEW_NOT_PENDING);
        }
    }

    public void softDelete(Instant now) {
        if (status == ProjectStatus.PENDING_REVIEW || status == ProjectStatus.ONGOING) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_DELETABLE);
        }
        this.deletedAt = now;
        touch();
    }

    /** 마감일이 확정되지 않은 프로젝트는 dDay를 계산할 수 없어 null을 반환한다. */
    public Integer dDay(Instant now) {
        if (fundingDeadline == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(now.truncatedTo(ChronoUnit.DAYS),
                fundingDeadline.truncatedTo(ChronoUnit.DAYS));
    }

    public int achievementRate(long currentAmount) {
        if (goalAmount == null || goalAmount == 0) {
            return 0;
        }
        return (int) (currentAmount * 100 / goalAmount);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
