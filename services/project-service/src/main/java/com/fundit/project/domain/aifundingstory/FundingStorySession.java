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
    /** run 추적자와 폐기 선점 행만 채운다 — run ID를 받기 전 폐기 요청을 매칭하는 유일한 식별자. */
    private String idempotencyKey;
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

    public static FundingStorySession trackRun(
            UUID runId, Long projectId, UUID sellerId, UUID sessionId, String idempotencyKey) {
        FundingStorySession run = trackRun(runId, projectId, sellerId, sessionId);
        run.idempotencyKey = idempotencyKey;
        return run;
    }

    /**
     * run ID를 받기 전에 폐기 요청이 온 경우의 선점 행(QA-189). {@code createRun}이 같은 키로 이 행을
     * 발견하면 그때 만드는 run 추적자를 바로 폐기 상태로 저장한다.
     */
    public static FundingStorySession preemptiveDiscard(
            UUID id, Long projectId, UUID sellerId, String idempotencyKey) {
        FundingStorySession preempt = create(
                id, projectId, sellerId, RUN_PREFIX + "discard-preempt", List.of(), List.of());
        preempt.idempotencyKey = idempotencyKey;
        preempt.status = FundingStorySessionStatus.DISCARDED;
        return preempt;
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

    public boolean isDiscarded() {
        return status == FundingStorySessionStatus.DISCARDED;
    }

    /** 생성 중인 run을 폐기한다. 이미 폐기면 no-op, 이미 터미널이면 반영된 결과를 되돌리지 않는다. */
    public boolean discard() {
        if (!isRunTracker()) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
        if (status != FundingStorySessionStatus.GENERATING) {
            return false;
        }
        this.status = FundingStorySessionStatus.DISCARDED;
        return true;
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
        if (isDiscarded()) {
            // 폐기된 run은 결과를 확정하지 않는다. CONFLICT를 던지면 AI가 callback을 재시도한다(QA-189).
            return false;
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
