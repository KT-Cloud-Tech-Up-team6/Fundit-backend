package com.fundit.project.domain.aifundingstory;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — GENERATING→COMPLETED/FAILED
 * 상태 전이 규칙이 있어 도메인/영속성을 완전히 분리한다.
 */
@Getter
@Builder(toBuilder = true)
public class FundingStorySession {

    private static final String SESSION_PREFIX = "SESSION:";
    private static final String RUN_PREFIX = "RUN:";

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

    public static FundingStorySession trackSession(
            UUID id, Long projectId, UUID sellerId, String coreFingerprint) {
        return create(id, projectId, sellerId, SESSION_PREFIX + coreFingerprint, List.of(), List.of());
    }

    public static FundingStorySession trackRun(
            UUID runId, Long projectId, UUID sellerId, UUID sessionId) {
        return create(runId, projectId, sellerId, RUN_PREFIX + sessionId, List.of(), List.of());
    }

    public boolean isSessionTracker() {
        return productDescription != null && productDescription.startsWith(SESSION_PREFIX);
    }

    public boolean isRunTracker() {
        return productDescription != null && productDescription.startsWith(RUN_PREFIX);
    }

    public String getCoreFingerprint() {
        return isSessionTracker() ? productDescription.substring(SESSION_PREFIX.length()) : null;
    }

    public void confirmCoreFingerprint(String coreFingerprint) {
        if (!isSessionTracker()) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
        this.productDescription = SESSION_PREFIX + coreFingerprint;
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

    /** Returns false for an identical terminal callback and rejects a conflicting replay. */
    public boolean finishRun(FundingStoryResult terminalResult) {
        if (!isRunTracker()) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
        if (status != FundingStorySessionStatus.GENERATING) {
            if (Objects.equals(result, terminalResult)) {
                return false;
            }
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 다른 완료 결과가 확정되었습니다.");
        }
        this.result = terminalResult;
        this.additionalQuestions = List.of();
        this.status = "failed".equals(terminalResult.status())
                ? FundingStorySessionStatus.FAILED
                : FundingStorySessionStatus.COMPLETED;
        return true;
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
