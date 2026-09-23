package com.fundit.project.domain.project;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — DRAFT→ONGOING 상태 전이 규칙과
 * 목표금액 등 불변식이 있어 도메인/영속성을 완전히 분리한다. 관리자 심사 단계는 폐지됐다
 * (필수 항목 완료 시 바로 공개) — {@link #publish} 참고.
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
    /** 펀딩 마감 감시(FundingDeadlineWatcher)가 project.funding-deadline-reached.v1을 중복 발행하지 않도록 남기는 표시. */
    private Instant deadlineNotifiedAt;
    /** 생성 요청의 Idempotency-Key(선택). order-service Funding과 동일 패턴 — 셀러 범위로 중복 생성을 막는다. */
    private final String idempotencyKey;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * DRAFT는 비공개다. 공개 상세(PROJECT-020)뿐 아니라 리워드/새소식/커뮤니티/
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

    /**
     * 필수 작성 항목이 모두 채워진 DRAFT 상태에서 바로 공개(ONGOING)로 전환한다. 이 시점에
     * 펀딩 기간이 처음 확정된다. 관리자 심사 단계는 폐지됐다 — DRAFT 다음은 바로 ONGOING이다.
     */
    public void publish(Instant fundingStartAt, Instant fundingDeadline) {
        if (status != ProjectStatus.DRAFT || !hasCompletedBasicInfo() || !hasStory()) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_SUBMITTABLE);
        }
        this.status = ProjectStatus.ONGOING;
        this.fundingStartAt = fundingStartAt;
        this.fundingDeadline = fundingDeadline;
    }

    /** FundingDeadlineWatcher 전용 — 마감 도래를 이미 통지했음을 표시해 중복 발행을 막는다. */
    public void markDeadlineNotified() {
        this.deadlineNotifiedAt = Instant.now();
    }
}
