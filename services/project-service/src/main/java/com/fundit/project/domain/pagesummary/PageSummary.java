package com.fundit.project.domain.pagesummary;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 상세 페이지 AI 요약(#169) — 프로젝트당 1개. 입력 해시가 바뀔 때만 {@code sourceRevision}을 올려 AI에
 * 새 요청을 보내고, 같은 revision 안에서는 {@code attempt}로 재접수(서명 URL 만료)를 구분한다.
 */
@Getter
@Builder(toBuilder = true)
public class PageSummary {

    /** AI가 retryable로 알린 실패의 재시도 한도(AI팀 합의: 30초·60초 후 최대 2회). */
    public static final int MAX_RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);
    /** 결과가 이 시간 안에 오지 않으면 실패로 닫는다 — 서명 URL 유효기간(60분)보다 짧아야 한다. */
    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Long projectId;
    /** 요약 입력이 바뀌었을 수 있는 마지막 쓰기 시각. 쓰기 경로만 올린다. */
    private final Instant dirtyAt;
    /** 워커가 마지막으로 반영한 {@code dirtyAt}. */
    private Instant handledDirtyAt;
    private String contentHash;
    private int sourceRevision;
    private int attempt;
    private UUID runId;
    private PageSummaryStatus status;
    private List<PageSummarySection> sections;
    private String errorCode;
    private int retryCount;
    private Instant nextAttemptAt;
    private Instant requestedAt;
    private Instant completedAt;

    public boolean isDirty() {
        return handledDirtyAt == null || dirtyAt.isAfter(handledDirtyAt);
    }

    public boolean hasSameContent(String hash) {
        return hash.equals(contentHash);
    }

    /** 입력이 그대로라 새 요청 없이 확인만 끝낸다. */
    public void markChecked() {
        this.handledDirtyAt = dirtyAt;
    }

    /** 입력이 바뀌었다 — 새 revision을 연다. 이전 결과는 현재 내용과 달라 더 보여주지 않는다. */
    public void startRevision(String hash) {
        this.handledDirtyAt = dirtyAt;
        this.contentHash = hash;
        this.sourceRevision++;
        this.attempt = 0;
        this.sections = null;
        this.errorCode = null;
        this.completedAt = null;
    }

    public boolean isFirstRevision() {
        return sourceRevision == 1;
    }

    /** 다음 run 생성에 쓸 멱등키. 만료 재접수는 같은 revision에 새 키가 필요해 시도 번호를 붙인다(AI팀 합의). */
    public String nextIdempotencyKey(UUID projectPublicId) {
        return projectPublicId + ":" + sourceRevision + ":" + (attempt + 1);
    }

    public void requested(UUID runId, Instant now) {
        this.attempt++;
        this.runId = runId;
        this.status = PageSummaryStatus.REQUESTED;
        this.retryCount = 0;
        this.nextAttemptAt = now;
        this.requestedAt = now;
    }

    public boolean canRetry() {
        return retryCount < MAX_RETRIES;
    }

    public void waitRetry(Instant now) {
        this.retryCount++;
        this.status = PageSummaryStatus.RETRY_WAIT;
        this.nextAttemptAt = now.plus(RETRY_DELAY.multipliedBy(retryCount));
    }

    public void retried(Instant now) {
        this.status = PageSummaryStatus.REQUESTED;
        this.nextAttemptAt = now;
        this.requestedAt = now;
    }

    public boolean isTimedOut(Instant now) {
        return requestedAt != null && requestedAt.plus(TIMEOUT).isBefore(now);
    }

    public void succeed(List<PageSummarySection> sections, Instant now) {
        this.status = PageSummaryStatus.SUCCEEDED;
        this.sections = List.copyOf(sections);
        this.completedAt = now;
        this.nextAttemptAt = null;
    }

    public void fail(String errorCode, Instant now) {
        this.status = PageSummaryStatus.FAILED;
        this.errorCode = errorCode;
        this.completedAt = now;
        this.nextAttemptAt = null;
    }

    /** 공개 상세에 결과를 내려도 되는지 — 새 revision이 열리면 sections가 비워진다. */
    public boolean isSucceeded() {
        return status == PageSummaryStatus.SUCCEEDED;
    }

    /** 아직 결과가 없고 실패로 끝나지도 않은 상태(요청 전 포함). */
    public boolean isGenerating() {
        return status == null || status == PageSummaryStatus.REQUESTED || status == PageSummaryStatus.RETRY_WAIT;
    }
}
