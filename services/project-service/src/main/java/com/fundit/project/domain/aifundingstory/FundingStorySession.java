package com.fundit.project.domain.aifundingstory;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — GENERATING→COMPLETED/FAILED
 * 상태 전이 규칙이 있어 도메인/영속성을 완전히 분리한다.
 */
@Getter
@Builder(toBuilder = true)
public class FundingStorySession {

    private final UUID id;
    private final Long projectId;
    private final UUID sellerId;
    private String productDescription;
    private List<String> productImageUrls;
    private List<FundingStoryAnswer> answers;
    private FundingStorySessionStatus status;
    private List<FundingStoryAdditionalQuestion> additionalQuestions;
    private FundingStoryResult result;
    private final Instant createdAt;
    private Instant updatedAt;

    public static FundingStorySession create(UUID id, Long projectId, UUID sellerId,
                                              String productDescription, List<String> productImageUrls,
                                              List<FundingStoryAnswer> answers) {
        return FundingStorySession.builder()
                .id(id)
                .projectId(projectId)
                .sellerId(sellerId)
                .productDescription(productDescription)
                .productImageUrls(productImageUrls)
                .answers(answers)
                .status(FundingStorySessionStatus.GENERATING)
                .build();
    }

    public boolean isOwnedBy(UUID accountId) {
        return sellerId != null && sellerId.equals(accountId);
    }

    public boolean isCompleted() {
        return status == FundingStorySessionStatus.COMPLETED;
    }

    public void completeWith(FundingStoryResult result, List<FundingStoryAdditionalQuestion> additionalQuestions) {
        requireGenerating();
        this.result = result;
        this.additionalQuestions = additionalQuestions;
        this.status = FundingStorySessionStatus.COMPLETED;
    }

    public void fail() {
        requireGenerating();
        this.status = FundingStorySessionStatus.FAILED;
    }

    private void requireGenerating() {
        if (status != FundingStorySessionStatus.GENERATING) {
            throw new BusinessException(CommonErrorCode.BUSINESS_RULE_VIOLATION);
        }
    }
}
