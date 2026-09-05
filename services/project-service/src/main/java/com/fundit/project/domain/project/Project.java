package com.fundit.project.domain.project;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — DRAFT→PENDING_REVIEW→ONGOING/DRAFT
 * 상태 전이 규칙과 목표금액 등 불변식이 있어 도메인/영속성을 완전히 분리한다.
 */
@Getter
@Builder(toBuilder = true)
public class Project {

    private static final long MIN_GOAL_AMOUNT = 500_000L;

    private final Long id;
    private final UUID publicId;
    private final UUID sellerId;
    private BusinessType businessType;
    private String categoryMajor;
    private String categoryMinor;
    private String title;
    private Long goalAmount;
    private Instant fundingStartAt;
    private Instant fundingDeadline;
    private ProjectStatus status;
    private String coverImageUrl;
    private List<IntroContentBlock> introContent;
    private final String projectDisplayCode;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * DRAFT/PENDING_REVIEW는 비공개다. 공개 상세(PROJECT-020)뿐 아니라 리워드/새소식/커뮤니티/
     * LIVE검증처럼 프로젝트에 딸린 소비자용 하위 리소스 조회에도 동일 기준을 적용해, 존재 여부가
     * 간접적으로 노출되지 않게 한다(CLAUDE.md "미공개 프로젝트 존재 여부 비노출" 원칙).
     */
    public boolean isPublic() {
        return status == ProjectStatus.ONGOING || status == ProjectStatus.SUCCEEDED || status == ProjectStatus.FAILED;
    }

    public boolean isOwnedBy(UUID accountId) {
        return sellerId != null && sellerId.equals(accountId);
    }

    /** PATCH .../basic-info — 전달된(null이 아닌) 필드만 갱신하는 임시저장 겸용 API. */
    public void updateBasicInfo(BusinessType businessType, String categoryMajor, String categoryMinor,
                                 String title, Long goalAmount) {
        if (goalAmount != null && goalAmount < MIN_GOAL_AMOUNT) {
            throw new BusinessException(ProjectErrorCode.GOAL_AMOUNT_TOO_LOW);
        }
        if (businessType != null) this.businessType = businessType;
        if (categoryMajor != null) this.categoryMajor = categoryMajor;
        if (categoryMinor != null) this.categoryMinor = categoryMinor;
        if (title != null) this.title = title;
        if (goalAmount != null) this.goalAmount = goalAmount;
    }

    /** PATCH .../story — 전달된 필드만 갱신하는 임시저장 겸용 API. */
    public void updateStory(String title, String coverImageUrl, List<IntroContentBlock> introContent) {
        if (title != null) this.title = title;
        if (coverImageUrl != null) this.coverImageUrl = coverImageUrl;
        if (introContent != null) this.introContent = introContent;
    }

    public boolean hasCompletedBasicInfo() {
        return businessType != null && categoryMajor != null && categoryMinor != null
                && title != null && goalAmount != null;
    }

    public boolean hasStory() {
        return introContent != null && !introContent.isEmpty();
    }

    /** DRAFT 상태만 삭제 가능(소프트 삭제). */
    public void delete() {
        if (status != ProjectStatus.DRAFT) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_DELETABLE);
        }
        this.deletedAt = Instant.now();
    }

    /** 필수 작성 항목이 모두 채워진 DRAFT 상태에서만 심사 제출 가능. */
    public void submit() {
        if (status != ProjectStatus.DRAFT) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_SUBMITTABLE);
        }
        this.status = ProjectStatus.PENDING_REVIEW;
    }

    /** 심사 승인 — PENDING_REVIEW 상태에서만 가능. 이 시점에 펀딩 기간이 처음 확정된다. */
    public void approve(Instant fundingStartAt, Instant fundingDeadline) {
        if (status != ProjectStatus.PENDING_REVIEW) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_REVIEWABLE);
        }
        this.status = ProjectStatus.ONGOING;
        this.fundingStartAt = fundingStartAt;
        this.fundingDeadline = fundingDeadline;
    }

    /** 심사 반려 — PENDING_REVIEW 상태에서만 가능. 재제출 가능하도록 DRAFT로 되돌린다. */
    public void reject() {
        if (status != ProjectStatus.PENDING_REVIEW) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_REVIEWABLE);
        }
        this.status = ProjectStatus.DRAFT;
    }
}
